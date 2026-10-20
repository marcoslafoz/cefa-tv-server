package es.cefa.tv.core.aracast.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import es.cefa.tv.core.realtime.VirtualPlayerBridge;
import es.cefa.tv.core.shell.service.ShellSessionService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hace que un Aracast se comporte como un reproductor de CEFA TV ante el control remoto y la
 * terminal: recibe los mismos mensajes {@code REMOTE_START}, {@code INPUT} o {@code SHELL_OPEN} y
 * responde con fotogramas y salida de shell por los visores del registro, de modo que la interfaz
 * no distingue un tipo de otro.
 */
@Slf4j
@Component
public class AracastBridge implements VirtualPlayerBridge {

  private static final Duration ONLINE_WINDOW = Duration.ofSeconds(90);
  private static final int DEFAULT_COLS = 80;
  private static final int DEFAULT_ROWS = 24;
  private static final int MAX_RETRIES = 5;

  private final AracastPlayerRepository aracasts;
  private final AracastRemoteScreenService screens;
  private final AracastShellService shells;
  private final PlayerSessionRegistry registry;
  private final ShellSessionService shellSessions;
  private final ObjectMapper mapper;
  private final ApplicationEventPublisher events;
  private final Map<UUID, String> hosts = new ConcurrentHashMap<>();
  private final Map<UUID, Instant> seen = new ConcurrentHashMap<>();
  private final Map<UUID, Stream> streams = new ConcurrentHashMap<>();
  private final Map<UUID, AracastInputMapper> mappers = new ConcurrentHashMap<>();
  private final Map<UUID, ExecutorService> inputs = new ConcurrentHashMap<>();
  private final ExecutorService streaming = Executors.newCachedThreadPool();
  private volatile boolean loaded;

  /**
   * Crea el puente.
   *
   * @param aracasts datos de conexión de los Aracast
   * @param screens pantalla en vivo
   * @param shells terminal SSH
   * @param registry registro de sesiones, por donde se responde a los visores
   * @param shellSessions sesiones de terminal, a las que se entrega la salida
   * @param mapper serializador JSON
   * @param events publicador de eventos, para avisar de que se abre o se cierra una sesión
   */
  public AracastBridge(
      AracastPlayerRepository aracasts,
      AracastRemoteScreenService screens,
      AracastShellService shells,
      PlayerSessionRegistry registry,
      @Lazy ShellSessionService shellSessions,
      ObjectMapper mapper,
      ApplicationEventPublisher events) {
    this.aracasts = aracasts;
    this.screens = screens;
    this.shells = shells;
    this.registry = registry;
    this.shellSessions = shellSessions;
    this.mapper = mapper;
    this.events = events;
  }

  @Override
  public boolean handles(UUID playerId) {
    ensureLoaded();
    return hosts.containsKey(playerId);
  }

  @Override
  public boolean isOnline(UUID playerId) {
    Instant last = seen.get(playerId);
    return last != null && last.isAfter(Instant.now().minus(ONLINE_WINDOW));
  }

  /**
   * Anota que un Aracast ha respondido.
   *
   * @param playerId reproductor
   * @param host IP o nombre con el que se le gestiona
   */
  public void markSeen(UUID playerId, String host) {
    hosts.put(playerId, host);
    seen.put(playerId, Instant.now());
  }

  /**
   * Da a conocer un Aracast recién dado de alta o con otra dirección.
   *
   * @param playerId reproductor
   * @param host IP o nombre
   */
  public void register(UUID playerId, String host) {
    hosts.put(playerId, host);
  }

  /**
   * Olvida un Aracast dado de baja y cierra lo que tuviera abierto.
   *
   * @param playerId reproductor
   */
  public void forget(UUID playerId) {
    stopStream(playerId);
    shells.close(playerId);
    hosts.remove(playerId);
    seen.remove(playerId);
  }

  @Override
  public void onMessage(UUID playerId, String json) {
    ensureLoaded();
    try {
      JsonNode message = mapper.readTree(json);
      switch (message.path("type").asText()) {
        case "REMOTE_START" -> startStream(playerId);
        case "REMOTE_STOP" -> stopStream(playerId);
        case "INPUT" -> input(playerId, message);
        case "SHELL_OPEN" -> openShell(playerId, message);
        case "SHELL_INPUT" -> shells.input(playerId, message.path("data").asText(""));
        case "SHELL_RESIZE" ->
            shells.resize(
                playerId,
                message.path("cols").asInt(DEFAULT_COLS),
                message.path("rows").asInt(DEFAULT_ROWS));
        case "SHELL_CLOSE" -> shells.close(playerId);
        default -> log.trace("Mensaje sin efecto en un Aracast");
      }
    } catch (IOException | RuntimeException e) {
      log.debug("Mensaje no atendido para el Aracast {}: {}", playerId, e.getMessage());
    }
  }

  private void startStream(UUID playerId) {
    String host = hosts.get(playerId);
    if (host == null) {
      return;
    }
    Stream stream = new Stream();
    if (streams.putIfAbsent(playerId, stream) != null) {
      return;
    }
    events.publishEvent(new AracastRemoteSessionEvent(playerId, true));
    streaming.submit(() -> stream(playerId, host, stream));
  }

  private void stream(UUID playerId, String host, Stream stream) {
    long sequence = 0;
    int failures = 0;
    try {
      while (stream.running && registry.hasViewers(playerId)) {
        try {
          Optional<AracastRemoteScreenService.Frame> frame = screens.frame(host, sequence);
          failures = 0;
          if (frame.isPresent()) {
            sequence = frame.get().sequence();
            registry.broadcastBinary(playerId, ByteBuffer.wrap(frame.get().jpeg()));
          }
        } catch (AracastException e) {
          if (++failures > MAX_RETRIES) {
            registry.broadcastText(playerId, "{\"type\":\"STATE\",\"online\":false}");
            return;
          }
          Thread.sleep(1000);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      streams.remove(playerId, stream);
      screens.stop(host);
      events.publishEvent(new AracastRemoteSessionEvent(playerId, false));
    }
  }

  private void stopStream(UUID playerId) {
    Stream stream = streams.get(playerId);
    if (stream != null) {
      stream.running = false;
    }
    mappers.remove(playerId);
    ExecutorService executor = inputs.remove(playerId);
    if (executor != null) {
      executor.shutdown();
    }
  }

  private void input(UUID playerId, JsonNode message) {
    String host = hosts.get(playerId);
    if (host == null) {
      return;
    }
    AracastInputMapper mapper = mappers.computeIfAbsent(playerId, id -> new AracastInputMapper());
    ExecutorService executor =
        inputs.computeIfAbsent(playerId, id -> Executors.newSingleThreadExecutor());
    for (RemoteInput action : mapper.map(message)) {
      executor.submit(
          () -> {
            try {
              screens.input(host, action);
            } catch (RuntimeException e) {
              log.debug("Entrada no aplicada en {}: {}", host, e.getMessage());
            }
          });
    }
  }

  private void openShell(UUID playerId, JsonNode message) {
    String host = hosts.get(playerId);
    String sessionId = message.path("sessionId").asText();
    if (host == null) {
      return;
    }
    streaming.submit(
        () -> {
          try {
            shells.open(
                playerId,
                host,
                sessionId,
                message.path("cols").asInt(DEFAULT_COLS),
                message.path("rows").asInt(DEFAULT_ROWS),
                new AracastShellService.Listener() {
                  @Override
                  public void output(String id, String data) {
                    shellSessions.onPlayerOutput(playerId, id, data);
                  }

                  @Override
                  public void exit(String id, int code) {
                    shellSessions.onPlayerExit(playerId, id, code);
                  }
                });
          } catch (RuntimeException e) {
            log.warn("No se pudo abrir la terminal del Aracast {}: {}", host, e.getMessage());
            shellSessions.onPlayerExit(playerId, sessionId, 1);
          }
        });
  }

  /** Recarga la lista de Aracast conocidos. */
  @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
  void refresh() {
    loaded = false;
    ensureLoaded();
  }

  private synchronized void ensureLoaded() {
    if (loaded) {
      return;
    }
    Map<UUID, String> current = new ConcurrentHashMap<>();
    for (AracastPlayer aracast : aracasts.findAll()) {
      current.put(aracast.getPlayerId(), aracast.getHost());
    }
    hosts.keySet().retainAll(current.keySet());
    hosts.putAll(current);
    loaded = true;
  }

  /** Cierra todo al parar la aplicación. */
  @PreDestroy
  void shutdown() {
    streams.values().forEach(s -> s.running = false);
    streaming.shutdownNow();
    inputs.values().forEach(ExecutorService::shutdownNow);
  }

  private static final class Stream {
    volatile boolean running = true;
  }
}
