package es.cefa.tv.core.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** Registro de las conexiones WebSocket de reproductores y de los visores de control remoto. */
@Slf4j
@Component
public class PlayerSessionRegistry {

  private static final int SEND_TIME_LIMIT_MS = 5000;
  private static final int BUFFER_LIMIT_BYTES = 4 * 1024 * 1024;

  private final Map<UUID, WebSocketSession> players = new ConcurrentHashMap<>();
  private final Map<UUID, Set<WebSocketSession>> viewers = new ConcurrentHashMap<>();
  private final Map<UUID, Set<String>> remoteStarters = new ConcurrentHashMap<>();
  private final Map<UUID, String> remoteParams = new ConcurrentHashMap<>();

  /**
   * Registra la conexión de un reproductor y cierra la anterior si existía.
   *
   * @param playerId reproductor
   * @param session sesión recién abierta
   * @return la sesión decorada para envíos concurrentes
   */
  public WebSocketSession registerPlayer(UUID playerId, WebSocketSession session) {
    WebSocketSession decorated =
        new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
    WebSocketSession previous = players.put(playerId, decorated);
    if (previous != null && previous.isOpen()) {
      closeQuietly(previous);
    }
    return decorated;
  }

  /**
   * Elimina la conexión de un reproductor si sigue siendo la registrada.
   *
   * @param playerId reproductor
   * @param sessionId identificador de la sesión que se cierra
   * @return {@code true} si era la sesión vigente
   */
  public boolean unregisterPlayer(UUID playerId, String sessionId) {
    return players.computeIfPresent(
            playerId, (id, current) -> current.getId().equals(sessionId) ? null : current)
        == null;
  }

  /**
   * Indica si el reproductor tiene un WebSocket abierto.
   *
   * @param playerId reproductor
   * @return {@code true} si está conectado
   */
  public boolean isConnected(UUID playerId) {
    WebSocketSession session = players.get(playerId);
    return session != null && session.isOpen();
  }

  /**
   * Envía un mensaje de texto al reproductor.
   *
   * @param playerId reproductor
   * @param json contenido
   * @return {@code true} si se pudo enviar
   */
  public boolean sendToPlayer(UUID playerId, String json) {
    WebSocketSession session = players.get(playerId);
    if (session == null || !session.isOpen()) {
      return false;
    }
    try {
      session.sendMessage(new TextMessage(json));
      return true;
    } catch (IOException | RuntimeException e) {
      log.warn("No se pudo enviar al reproductor {}: {}", playerId, e.getMessage());
      return false;
    }
  }

  /**
   * Cierra la conexión de un reproductor.
   *
   * @param playerId reproductor
   */
  public void disconnectPlayer(UUID playerId) {
    WebSocketSession session = players.remove(playerId);
    if (session != null) {
      closeQuietly(session);
    }
  }

  /**
   * Añade un visor de control remoto.
   *
   * @param playerId reproductor observado
   * @param session sesión del navegador
   * @return la sesión decorada
   */
  public WebSocketSession addViewer(UUID playerId, WebSocketSession session) {
    WebSocketSession decorated =
        new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
    viewers.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(decorated);
    return decorated;
  }

  /**
   * Quita un visor.
   *
   * @param playerId reproductor observado
   * @param sessionId sesión que se cierra
   */
  public void removeViewer(UUID playerId, String sessionId) {
    Set<WebSocketSession> set = viewers.get(playerId);
    if (set != null) {
      set.removeIf(s -> s.getId().equals(sessionId));
    }
  }

  /**
   * Envía un texto a todos los visores de un reproductor.
   *
   * @param playerId reproductor
   * @param json contenido
   */
  public void broadcastText(UUID playerId, String json) {
    forEachViewer(playerId, session -> session.sendMessage(new TextMessage(json)));
  }

  /**
   * Envía un fotograma binario a todos los visores de un reproductor.
   *
   * @param playerId reproductor
   * @param frame bytes del JPEG
   */
  public void broadcastBinary(UUID playerId, java.nio.ByteBuffer frame) {
    forEachViewer(
        playerId, session -> session.sendMessage(new BinaryMessage(frame.asReadOnlyBuffer())));
  }

  /**
   * Indica si hay visores conectados.
   *
   * @param playerId reproductor
   * @return {@code true} si hay al menos uno
   */
  public boolean hasViewers(UUID playerId) {
    Set<WebSocketSession> set = viewers.get(playerId);
    return set != null && !set.isEmpty();
  }

  /**
   * Marca que un visor ha pedido el vídeo remoto.
   *
   * @param playerId reproductor
   * @param viewerSessionId sesión del visor
   * @param params parámetros {@code REMOTE_START} serializados
   * @return {@code true} si es el primer visor activo y hay que arrancar la captura
   */
  public boolean startRemote(UUID playerId, String viewerSessionId, String params) {
    Set<String> set = remoteStarters.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet());
    boolean first = set.isEmpty();
    set.add(viewerSessionId);
    remoteParams.put(playerId, params);
    return first;
  }

  /**
   * Marca que un visor ha dejado de pedir el vídeo remoto.
   *
   * @param playerId reproductor
   * @param viewerSessionId sesión del visor
   * @return {@code true} si era el último visor activo y hay que parar la captura
   */
  public boolean stopRemote(UUID playerId, String viewerSessionId) {
    Set<String> set = remoteStarters.get(playerId);
    if (set == null || !set.remove(viewerSessionId)) {
      return false;
    }
    if (set.isEmpty()) {
      remoteParams.remove(playerId);
      return true;
    }
    return false;
  }

  /**
   * Devuelve los parámetros de la sesión remota activa.
   *
   * @param playerId reproductor
   * @return el mensaje {@code REMOTE_START} o {@code null} si no hay sesión remota
   */
  public String activeRemoteParams(UUID playerId) {
    return remoteParams.get(playerId);
  }

  /** Envía un ping periódico para mantener vivas las conexiones a través de proxies. */
  @Scheduled(fixedDelay = 30000)
  public void pingAll() {
    players.values().forEach(this::ping);
    viewers.values().forEach(set -> set.forEach(this::ping));
  }

  /** Envía un ping a una sesión abierta. */
  private void ping(WebSocketSession session) {
    try {
      if (session.isOpen()) {
        session.sendMessage(new PingMessage());
      }
    } catch (IOException | RuntimeException e) {
      log.debug("Ping fallido en {}", session.getId());
    }
  }

  /** Ejecuta una acción sobre cada visor abierto del reproductor ignorando los fallos de envío. */
  private void forEachViewer(UUID playerId, SessionAction action) {
    Set<WebSocketSession> set = viewers.get(playerId);
    if (set == null) {
      return;
    }
    for (WebSocketSession session : set) {
      try {
        if (session.isOpen()) {
          action.run(session);
        }
      } catch (IOException | RuntimeException e) {
        log.debug("Envío fallido al visor {}", session.getId());
      }
    }
  }

  /** Cierra una sesión sin propagar errores. */
  private void closeQuietly(WebSocketSession session) {
    try {
      session.close(CloseStatus.NORMAL);
    } catch (IOException | RuntimeException e) {
      log.debug("Cierre fallido de {}", session.getId());
    }
  }

  @FunctionalInterface
  private interface SessionAction {
    void run(WebSocketSession session) throws IOException;
  }
}
