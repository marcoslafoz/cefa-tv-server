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
import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.delivery.service.DeliveryService;
import es.cefa.tv.core.manifest.model.PlayerManifest;
import es.cefa.tv.core.manifest.service.ManifestService;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerKind;
import es.cefa.tv.core.player.model.PlayerStatus;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.IdleLogoResolver;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shared.util.Hashes;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Entrega a los Aracast las versiones del manifest. Es el equivalente de la descarga que hace un
 * reproductor de CEFA TV, pero empujando: traduce el manifest a plantillas y eventos, lo publica y
 * marca la entrega como aplicada. Si el player no responde la versión queda en cola y se reintenta.
 * Cada hora (cada 30 minutos si hay ciclos de plantillas, que solo cubren las próximas horas) se
 * recalcula el plan para que las fechas de las programaciones se apliquen aunque no cambie nada, y
 * cualquier cambio de contenido, como el logo de la pantalla en reposo, lo revisa de inmediato.
 *
 * <p>Cada publicación, correcta o fallida, se deja en la auditoría con la acción {@code
 * ARACAST_PUBLISH}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AracastDeliveryService {

  /** Acción de auditoría de un envío a un Aracast. */
  public static final String AUDIT_ACTION = "ARACAST_PUBLISH";

  private static final Duration OFFLINE_RETRY = Duration.ofSeconds(30);
  private static final Duration FAILURE_RETRY = Duration.ofMinutes(10);
  private static final Duration REFRESH_EVERY = Duration.ofHours(1);
  private static final Duration REFRESH_ROLLING = Duration.ofMinutes(30);
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
  private final IdleLogoResolver idleLogos;
  private final AuditService audit;
  private final ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
  private final Set<UUID> running = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Retry> retries = new ConcurrentHashMap<>();
  private final Map<UUID, Instant> lastChecked = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> rolling = new ConcurrentHashMap<>();
  private final Map<UUID, String> lastFailure = new ConcurrentHashMap<>();

  /** Busca los Aracast con algo que entregar y lanza cada entrega en segundo plano. */
  @Scheduled(fixedDelay = 5000, initialDelay = 15_000)
  public void tick() {
    for (Player player : players.findByKindAndStatus(PlayerKind.ARACAST, PlayerStatus.ACTIVE)) {
      if (isDue(player) && running.add(player.getId())) {
        executor.submit(() -> run(player.getId()));
      }
    }
  }

  /**
   * Un cambio de contenido, como el logo de la pantalla en reposo, puede cambiar lo que hay que
   * publicar sin crear una versión nueva del manifest: se vuelve a revisar cada Aracast.
   *
   * @param event cambio de contenido
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onContentChanged(ContentChangedEvent event) {
    lastChecked.clear();
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
    Duration every = rolling.getOrDefault(player.getId(), false) ? REFRESH_ROLLING : REFRESH_EVERY;
    return checked == null || now.isAfter(checked.plus(every));
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
    ZoneId zone = zone(manifest);
    AracastPlan plan;
    try {
      plan =
          AracastPlanner.plan(
                  manifest.getManifest(),
                  LocalDateTime.now(zone),
                  idleLogos.effective(player).logo())
              .withVolume(player.getVolume());
    } catch (UnsupportedPlaylistException e) {
      fail(player, aracast, version, pending, e.getMessage(), FAILURE_RETRY);
      return;
    }
    rolling.put(playerId, plan.rolling());
    String hash = hash(plan);
    if (!pending && hash.equals(aracast.getPublishedHash())) {
      lastChecked.put(playerId, Instant.now());
      return;
    }
    if (plan.events().isEmpty() && aracast.getPublishedHash() == null) {
      if (pending) {
        playerService.recordSkipped(playerId, version);
      }
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
      AracastPublisher.Result result = publisher.publish(host, plan, zone);
      playerService.recordPublished(playerId, version, hash);
      retries.remove(playerId);
      lastFailure.remove(playerId);
      lastChecked.put(playerId, Instant.now());
      if (pending || !plan.rolling()) {
        audit(player, AuditOutcome.SUCCESS, details(version, host, result));
      }
    } catch (PlayerOfflineException e) {
      retries.put(
          playerId, new Retry(player.getDesiredVersion(), Instant.now().plus(OFFLINE_RETRY)));
    } catch (RuntimeException e) {
      log.warn("No se pudo publicar en el Aracast {}: {}", host, e.getMessage());
      fail(
          player,
          aracast,
          version,
          pending,
          "No se pudo publicar: " + e.getMessage(),
          FAILURE_RETRY);
    }
  }

  private void fail(
      Player player,
      AracastPlayer aracast,
      long version,
      boolean pending,
      String message,
      Duration wait) {
    String clipped =
        message != null && message.length() > MESSAGE_LIMIT
            ? message.substring(0, MESSAGE_LIMIT)
            : message;
    UUID playerId = player.getId();
    playerService.recordFailure(playerId, version, pending, clipped);
    Player fresh = players.findById(playerId).orElse(player);
    retries.put(playerId, new Retry(fresh.getDesiredVersion(), Instant.now().plus(wait)));
    lastChecked.put(playerId, Instant.now());
    String key = version + "|" + clipped;
    if (!key.equals(lastFailure.put(playerId, key))) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("version", version);
      details.put("host", aracast.getHost());
      details.put("error", clipped);
      audit(player, AuditOutcome.FAILED, details);
    }
  }

  private void audit(Player player, AuditOutcome outcome, Map<String, Object> details) {
    audit.recordSystem(
        AUDIT_ACTION, "PLAYER", player.getId().toString(), player.getName(), outcome, details);
  }

  private static Map<String, Object> details(
      long version, String host, AracastPublisher.Result result) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("version", version);
    details.put("host", host);
    details.put("templatesInstalled", result.templatesInstalled());
    details.put("contentsUploaded", result.contentsUploaded());
    details.put("events", result.events());
    details.put("templatesRemoved", result.templatesRemoved());
    return details;
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
