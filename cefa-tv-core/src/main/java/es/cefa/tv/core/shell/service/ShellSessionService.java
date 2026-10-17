package es.cefa.tv.core.shell.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.shell.model.ShellCloseReason;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Relé de las sesiones de terminal entre el navegador del administrador y el reproductor. Admite
 * una sola sesión activa por reproductor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShellSessionService {

  private static final int DEFAULT_COLS = 80;
  private static final int DEFAULT_ROWS = 24;
  private static final int MAX_DIMENSION = 500;
  private static final int MAX_INPUT_CHARS = 65536;
  private static final int SEND_TIME_LIMIT_MS = 5000;
  private static final int BUFFER_LIMIT_BYTES = 2 * 1024 * 1024;

  private final PlayerRepository players;
  private final PlayerSessionRegistry registry;
  private final ShellAuditService auditService;
  private final CoreProperties properties;
  private final ObjectMapper objectMapper;
  private final Map<UUID, ShellSession> sessions = new ConcurrentHashMap<>();

  /**
   * Abre una sesión de terminal en un reproductor.
   *
   * @param playerId reproductor
   * @param viewer conexión del navegador
   * @param user administrador que la abre
   * @param cols columnas pedidas, o 0 para el valor por defecto
   * @param rows filas pedidas, o 0 para el valor por defecto
   */
  public synchronized void open(
      UUID playerId, WebSocketSession viewer, AuthenticatedUser user, int cols, int rows) {
    Player player = players.findById(playerId).orElse(null);
    if (player == null) {
      sendError(viewer, "PLAYER_NOT_FOUND");
      return;
    }
    if (!registry.isConnected(playerId)) {
      sendError(viewer, "PLAYER_OFFLINE");
      return;
    }
    if (player.getSystemInfo() != null
        && Boolean.FALSE.equals(player.getSystemInfo().get("shellEnabled"))) {
      sendError(viewer, "SHELL_DISABLED");
      return;
    }
    if (sessions.containsKey(playerId)) {
      sendError(viewer, "SHELL_BUSY");
      return;
    }
    WebSocketSession decorated =
        new ConcurrentWebSocketSessionDecorator(viewer, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
    UUID auditId = auditService.open(playerId, player.getName(), user, AuditClient.of(viewer));
    ShellSession session = new ShellSession(playerId, decorated, auditId);
    sessions.put(playerId, session);
    Map<String, Object> open = new LinkedHashMap<>();
    open.put("type", "SHELL_OPEN");
    open.put("sessionId", session.id);
    open.put("cols", dimension(cols, DEFAULT_COLS));
    open.put("rows", dimension(rows, DEFAULT_ROWS));
    if (!registry.sendToPlayer(playerId, toJson(open))) {
      sendError(decorated, "PLAYER_OFFLINE");
      finish(session, ShellCloseReason.PLAYER_UNREACHABLE, false);
      return;
    }
    send(decorated, Map.of("type", "OPENED", "sessionId", session.id));
  }

  /**
   * Reenvía al reproductor lo que escribe el usuario.
   *
   * @param playerId reproductor
   * @param viewerId conexión del navegador que escribe
   * @param data texto introducido
   */
  public void input(UUID playerId, String viewerId, String data) {
    ShellSession session = ownedSession(playerId, viewerId);
    if (session == null || data == null || data.length() > MAX_INPUT_CHARS) {
      return;
    }
    session.bytesIn.addAndGet(data.getBytes(StandardCharsets.UTF_8).length);
    session.lastInputAt = Instant.now();
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", "SHELL_INPUT");
    message.put("sessionId", session.id);
    message.put("data", data);
    registry.sendToPlayer(playerId, toJson(message));
  }

  /**
   * Reenvía al reproductor un cambio de tamaño de la terminal.
   *
   * @param playerId reproductor
   * @param viewerId conexión del navegador
   * @param cols columnas
   * @param rows filas
   */
  public void resize(UUID playerId, String viewerId, int cols, int rows) {
    ShellSession session = ownedSession(playerId, viewerId);
    if (session == null) {
      return;
    }
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", "SHELL_RESIZE");
    message.put("sessionId", session.id);
    message.put("cols", dimension(cols, DEFAULT_COLS));
    message.put("rows", dimension(rows, DEFAULT_ROWS));
    registry.sendToPlayer(playerId, toJson(message));
  }

  /**
   * Cierra la sesión por decisión del navegador.
   *
   * @param playerId reproductor
   * @param viewerId conexión del navegador
   * @param reason motivo del cierre
   */
  public void closeByViewer(UUID playerId, String viewerId, ShellCloseReason reason) {
    ShellSession session = ownedSession(playerId, viewerId);
    if (session != null) {
      finish(session, reason, true);
    }
  }

  /**
   * Entrega al navegador la salida del shell del reproductor.
   *
   * @param playerId reproductor
   * @param sessionId sesión de terminal
   * @param data salida del shell
   */
  public void onPlayerOutput(UUID playerId, String sessionId, String data) {
    ShellSession session = sessions.get(playerId);
    if (session == null || !session.id.equals(sessionId) || data == null) {
      return;
    }
    session.bytesOut.addAndGet(data.getBytes(StandardCharsets.UTF_8).length);
    send(session.viewer, Map.of("type", "OUTPUT", "data", data));
  }

  /**
   * Procesa el fin del shell en el reproductor.
   *
   * @param playerId reproductor
   * @param sessionId sesión de terminal
   * @param code código de salida
   */
  public void onPlayerExit(UUID playerId, String sessionId, int code) {
    ShellSession session = sessions.get(playerId);
    if (session == null || !session.id.equals(sessionId)) {
      return;
    }
    send(session.viewer, Map.of("type", "EXIT", "code", code));
    finish(session, ShellCloseReason.PROCESS_EXIT, false);
  }

  /**
   * Cierra la sesión cuando el reproductor pierde la conexión.
   *
   * @param playerId reproductor
   */
  public void onPlayerDisconnected(UUID playerId) {
    ShellSession session = sessions.get(playerId);
    if (session != null) {
      sendError(session.viewer, "PLAYER_DISCONNECTED");
      finish(session, ShellCloseReason.PLAYER_DISCONNECTED, false);
    }
  }

  /** Cierra las sesiones inactivas o que superan la duración máxima. */
  @Scheduled(fixedDelayString = "${cefa.tv.shell.check-interval-ms:30000}")
  public void expireSessions() {
    expireSessions(Instant.now());
  }

  /**
   * Cierra las sesiones inactivas o que superan la duración máxima en un instante dado.
   *
   * @param now instante de la comprobación
   */
  public void expireSessions(Instant now) {
    Duration idle = Duration.ofSeconds(properties.shell().idleTimeoutSec());
    Duration max = Duration.ofSeconds(properties.shell().maxDurationSec());
    for (ShellSession session : sessions.values()) {
      if (Duration.between(session.openedAt, now).compareTo(max) >= 0) {
        sendError(session.viewer, ShellCloseReason.MAX_DURATION.name());
        finish(session, ShellCloseReason.MAX_DURATION, true);
      } else if (Duration.between(session.lastInputAt, now).compareTo(idle) >= 0) {
        sendError(session.viewer, ShellCloseReason.IDLE_TIMEOUT.name());
        finish(session, ShellCloseReason.IDLE_TIMEOUT, true);
      }
    }
  }

  /**
   * Indica si un reproductor tiene una sesión activa.
   *
   * @param playerId reproductor
   * @return {@code true} si hay una sesión abierta
   */
  public boolean hasSession(UUID playerId) {
    return sessions.containsKey(playerId);
  }

  /**
   * Finaliza una sesión: la retira, avisa al reproductor si procede, audita y cierra el navegador.
   */
  private void finish(ShellSession session, ShellCloseReason reason, boolean notifyPlayer) {
    if (!sessions.remove(session.playerId, session)) {
      return;
    }
    if (notifyPlayer) {
      Map<String, Object> close = new LinkedHashMap<>();
      close.put("type", "SHELL_CLOSE");
      close.put("sessionId", session.id);
      registry.sendToPlayer(session.playerId, toJson(close));
    }
    auditService.close(session.auditId, session.bytesIn.get(), session.bytesOut.get(), reason);
    closeQuietly(session.viewer);
  }

  /** Devuelve la sesión del reproductor si pertenece a la conexión indicada. */
  private ShellSession ownedSession(UUID playerId, String viewerId) {
    ShellSession session = sessions.get(playerId);
    return session != null && session.viewer.getId().equals(viewerId) ? session : null;
  }

  /** Limita un tamaño de terminal a un rango válido. */
  private static int dimension(int value, int fallback) {
    return value <= 0 ? fallback : Math.min(value, MAX_DIMENSION);
  }

  /** Envía un mensaje de error al navegador. */
  private void sendError(WebSocketSession viewer, String message) {
    send(viewer, Map.of("type", "ERROR", "message", message));
  }

  /** Envía un mensaje JSON al navegador ignorando los fallos de envío. */
  private void send(WebSocketSession viewer, Map<String, Object> message) {
    try {
      if (viewer.isOpen()) {
        viewer.sendMessage(new TextMessage(toJson(message)));
      }
    } catch (IOException | RuntimeException e) {
      log.debug("Envío fallido al navegador {}: {}", viewer.getId(), e.getMessage());
    }
  }

  /** Cierra una conexión sin propagar errores. */
  private void closeQuietly(WebSocketSession session) {
    try {
      if (session.isOpen()) {
        session.close(CloseStatus.NORMAL);
      }
    } catch (IOException | RuntimeException e) {
      log.debug("Cierre fallido de {}", session.getId());
    }
  }

  /** Serializa un mensaje a JSON. */
  private String toJson(Map<String, Object> message) {
    try {
      return objectMapper.writeValueAsString(message);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
