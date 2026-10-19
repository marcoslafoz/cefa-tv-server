package es.cefa.tv.core.aracast.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.exception.PlayerOfflineException;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.plan.AracastPlan;
import es.cefa.tv.core.aracast.plan.AracastPlanner;
import es.cefa.tv.core.aracast.plan.UnsupportedPlaylistException;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.aracast.service.AracastPlayerService;
import es.cefa.tv.core.delivery.service.DeliveryService;
import es.cefa.tv.core.manifest.model.PlayerManifest;
import es.cefa.tv.core.manifest.service.ManifestService;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerKind;
import es.cefa.tv.core.player.model.PlayerStatus;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shared.util.Hashes;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Entrega a los Aracast las versiones del manifest. Es el equivalente de la descarga que hace un
 * reproductor de CEFA TV, pero empujando: traduce el manifest a plantillas y eventos, lo publica y
 * marca la entrega como aplicada. Si el player no responde la versión queda en cola y se reintenta;
 * cada hora se vuelve a calcular la ventana de siete días para que las fechas de las programaciones
 * se apliquen aunque no cambie nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AracastDeliveryService {

  private static final Duration OFFLINE_RETRY = Duration.ofSeconds(30);
  private static final Duration FAILURE_RETRY = Duration.ofMinutes(10);
  private static final Duration REFRESH_EVERY = Duration.ofHours(1);
  private static final int REACHABLE_TIMEOUT_MS = 3000;
  private static final int PARALLELISM = 4;
  private static final int MESSAGE_LIMIT = 500;

  private final PlayerRepository players;
  private final AracastPlayerRepository aracasts;
  private final ManifestService manifests;
  private final AracastPublisher publisher;
  private final AracastPlayerService playerService;
  private final DeliveryService deliveries;
  private final AracastCommandClient commands;
  private final ObjectMapper mapper;
  private final ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
  private final Set<UUID> running = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Retry> retries = new ConcurrentHashMap<>();
  private final Map<UUID, Instant> lastChecked = new ConcurrentHashMap<>();

  /** Busca los Aracast con algo que entregar y lanza cada entrega en segundo plano. */
  @Scheduled(fixedDelay = 5000, initialDelay = 15_000)
  public void tick() {
    for (Player player : players.findByKindAndStatus(PlayerKind.ARACAST, PlayerStatus.ACTIVE)) {
      if (isDue(player) && running.add(player.getId())) {
        executor.submit(() -> run(player.getId()));
      }
    }
  }

  private boolean isDue(Player player) {
    Instant now = Instant.now();
    Retry retry = retries.get(player.getId());
    if (retry != null
        && retry.version() == player.getDesiredVersion()
        && now.isBefore(retry.notBefore())) {
      return false;
    }
    if (player.isPending()) {
      return true;
    }
    Instant checked = lastChecked.get(player.getId());
    return checked == null || now.isAfter(checked.plus(REFRESH_EVERY));
  }

  private void run(UUID playerId) {
    try {
      deliver(playerId);
    } catch (RuntimeException e) {
      log.error("Fallo inesperado entregando al Aracast {}", playerId, e);
    } finally {
      running.remove(playerId);
    }
  }

  void deliver(UUID playerId) {
    Player player = players.findById(playerId).orElse(null);
    AracastPlayer aracast = aracasts.findById(playerId).orElse(null);
    if (player == null || aracast == null) {
      return;
    }
    PlayerManifest manifest;
    try {
      manifest = manifests.latest(playerId);
    } catch (ApiException e) {
      lastChecked.put(playerId, Instant.now());
      return;
    }
    long version = manifest.getVersion();
    boolean pending = player.isPending();
    AracastPlan plan;
    try {
      plan = AracastPlanner.plan(manifest.getManifest(), today(manifest));
    } catch (UnsupportedPlaylistException e) {
      fail(playerId, version, pending, e.getMessage(), FAILURE_RETRY);
      return;
    }
    String hash = hash(plan);
    if (!pending && hash.equals(aracast.getPublishedHash())) {
      lastChecked.put(playerId, Instant.now());
      return;
    }
    String host = aracast.getHost();
    if (!commands.isReachable(host, REACHABLE_TIMEOUT_MS)) {
      retries.put(
          playerId, new Retry(player.getDesiredVersion(), Instant.now().plus(OFFLINE_RETRY)));
      return;
    }
    if (pending) {
      deliveries.markDownloading(playerId, version);
    }
    try {
      publisher.publish(host, plan, zone(manifest));
      playerService.recordPublished(playerId, version, hash);
      retries.remove(playerId);
      lastChecked.put(playerId, Instant.now());
    } catch (PlayerOfflineException e) {
      retries.put(
          playerId, new Retry(player.getDesiredVersion(), Instant.now().plus(OFFLINE_RETRY)));
    } catch (RuntimeException e) {
      log.warn("No se pudo publicar en el Aracast {}: {}", host, e.getMessage());
      fail(playerId, version, pending, "No se pudo publicar: " + e.getMessage(), FAILURE_RETRY);
    }
  }

  private void fail(UUID playerId, long version, boolean pending, String message, Duration wait) {
    String clipped =
        message != null && message.length() > MESSAGE_LIMIT
            ? message.substring(0, MESSAGE_LIMIT)
            : message;
    playerService.recordFailure(playerId, version, pending, clipped);
    Player player = players.findById(playerId).orElse(null);
    long desired = player == null ? version : player.getDesiredVersion();
    retries.put(playerId, new Retry(desired, Instant.now().plus(wait)));
    lastChecked.put(playerId, Instant.now());
  }

  private LocalDate today(PlayerManifest manifest) {
    return LocalDate.now(zone(manifest));
  }

  private ZoneId zone(PlayerManifest manifest) {
    String timezone = manifest.getManifest().timezone();
    try {
      return timezone == null ? ZoneId.systemDefault() : ZoneId.of(timezone);
    } catch (RuntimeException e) {
      return ZoneId.systemDefault();
    }
  }

  private String hash(AracastPlan plan) {
    try {
      return Hashes.sha256Hex(mapper.writeValueAsString(plan));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo calcular el resumen del plan", e);
    }
  }

  /** Cierra el pool al parar la aplicación. */
  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  private record Retry(long version, Instant notBefore) {}
}
