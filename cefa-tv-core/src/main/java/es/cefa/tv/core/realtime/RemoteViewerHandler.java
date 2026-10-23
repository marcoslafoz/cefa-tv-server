package es.cefa.tv.core.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Gestiona el WebSocket {@code /ws/remote/{playerId}} del navegador que controla una pantalla. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteViewerHandler extends TextWebSocketHandler {

  /** Atributo de sesión con el identificador del reproductor observado. */
  public static final String PLAYER_ID = "playerId";

  /** Atributo de sesión con el usuario autenticado que controla la pantalla. */
  public static final String USER = "user";

  private static final String STARTED_AT = "remoteStartedAt";
  private static final String INPUT_COUNT = "remoteInputs";
  private static final String PLAYER_NAME = "remotePlayerName";

  private static final int DEFAULT_FPS = 8;
  private static final int DEFAULT_QUALITY = 60;
  private static final int DEFAULT_MAX_WIDTH = 1280;
  private static final int MAX_TEXT_LENGTH = 4096;
  private static final Set<String> INPUT_KINDS =
      Set.of("MOUSE_MOVE", "MOUSE_DOWN", "MOUSE_UP", "MOUSE_WHEEL", "KEY_DOWN", "KEY_UP", "TEXT");

  private final PlayerSessionRegistry registry;
  private final ObjectMapper objectMapper;
  private final PlayerRepository players;
  private final AuditService audit;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    UUID playerId = playerId(session);
    WebSocketSession decorated = registry.addViewer(playerId, session);
    decorated.sendMessage(
        new TextMessage("{\"type\":\"STATE\",\"online\":" + registry.isConnected(playerId) + "}"));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    UUID playerId = playerId(session);
    JsonNode json = objectMapper.readTree(message.getPayload());
    switch (json.path("type").asText()) {
      case "START" -> start(playerId, session, json);
      case "STOP" -> stop(playerId, session);
      case "INPUT" -> forwardInput(playerId, session, json, message.getPayload());
      default -> log.debug("Mensaje de visor ignorado");
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.warn(
        "Error de transporte en el visor remoto {} del reproductor {}: {}",
        session.getId(),
        playerId(session),
        exception.toString());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    UUID playerId = playerId(session);
    log.info(
        "Visor remoto {} del reproductor {} cerrado: código {} {}",
        session.getId(),
        playerId,
        status.getCode(),
        status.getReason());
    stop(playerId, session);
    registry.removeViewer(playerId, session.getId());
  }

  /** Retransmite al reproductor una entrada de ratón o teclado si su forma es válida. */
  private void forwardInput(
      UUID playerId, WebSocketSession session, JsonNode input, String payload) {
    boolean knownKind = INPUT_KINDS.contains(input.path("kind").asText());
    boolean textTooLong = input.path("text").asText("").length() > MAX_TEXT_LENGTH;
    if (knownKind && !textTooLong) {
      AtomicLong inputs = (AtomicLong) session.getAttributes().get(INPUT_COUNT);
      if (inputs != null) {
        inputs.incrementAndGet();
      }
      registry.sendToPlayer(playerId, payload);
    } else {
      log.debug("Entrada remota descartada");
    }
  }

  /** Registra la petición de vídeo remoto y arranca la captura si es el primer visor. */
  private void start(UUID playerId, WebSocketSession session, JsonNode request) {
    ObjectNode start = objectMapper.createObjectNode();
    start.put("type", "REMOTE_START");
    start.put("fps", request.path("fps").asInt(DEFAULT_FPS));
    start.put("quality", request.path("quality").asInt(DEFAULT_QUALITY));
    start.put("maxWidth", request.path("maxWidth").asInt(DEFAULT_MAX_WIDTH));
    String payload = start.toString();
    recordStart(playerId, session);
    if (registry.startRemote(playerId, session.getId(), payload)) {
      registry.sendToPlayer(playerId, payload);
    }
  }

  /** Retira la petición de vídeo remoto y para la captura si era el último visor. */
  private void stop(UUID playerId, WebSocketSession session) {
    recordStop(playerId, session);
    if (registry.stopRemote(playerId, session.getId())) {
      registry.sendToPlayer(playerId, "{\"type\":\"REMOTE_STOP\"}");
    }
  }

  /** Deja constancia del inicio de la sesión de control remoto, una sola vez por conexión. */
  private void recordStart(UUID playerId, WebSocketSession session) {
    Map<String, Object> attributes = session.getAttributes();
    if (attributes.containsKey(STARTED_AT)
        || !(attributes.get(USER) instanceof AuthenticatedUser user)) {
      return;
    }
    String name = players.findById(playerId).map(Player::getName).orElse(null);
    attributes.put(STARTED_AT, Instant.now());
    attributes.put(INPUT_COUNT, new AtomicLong());
    attributes.put(PLAYER_NAME, name == null ? "" : name);
    audit.recordUser(
        user,
        AuditClient.of(session),
        "PLAYER_REMOTE_START",
        "PLAYER",
        playerId.toString(),
        name,
        AuditOutcome.SUCCESS,
        Map.of());
  }

  /** Deja constancia del fin de la sesión de control remoto con su duración y entradas. */
  private void recordStop(UUID playerId, WebSocketSession session) {
    Map<String, Object> attributes = session.getAttributes();
    Object started = attributes.remove(STARTED_AT);
    if (!(started instanceof Instant startedAt)
        || !(attributes.get(USER) instanceof AuthenticatedUser user)) {
      return;
    }
    AtomicLong inputs = (AtomicLong) attributes.remove(INPUT_COUNT);
    Object name = attributes.remove(PLAYER_NAME);
    audit.recordUser(
        user,
        AuditClient.of(session),
        "PLAYER_REMOTE_STOP",
        "PLAYER",
        playerId.toString(),
        name == null ? null : name.toString(),
        AuditOutcome.SUCCESS,
        Map.of(
            "durationSec",
            Duration.between(startedAt, Instant.now()).toSeconds(),
            "inputs",
            inputs == null ? 0L : inputs.get()));
  }

  /** Obtiene el reproductor observado por la sesión. */
  private UUID playerId(WebSocketSession session) {
    return (UUID) session.getAttributes().get(PLAYER_ID);
  }
}
