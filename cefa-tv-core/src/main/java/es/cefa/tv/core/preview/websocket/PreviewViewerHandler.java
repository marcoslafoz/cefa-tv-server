package es.cefa.tv.core.preview.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
import es.cefa.tv.core.preview.service.PreviewService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Gestiona el WebSocket {@code /ws/previews}: el navegador se suscribe a la vista previa de varias
 * pantallas y recibe los fotogramas como mensajes binarios.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreviewViewerHandler extends TextWebSocketHandler {

  /** Atributo de sesión con el usuario autenticado. */
  public static final String USER = "user";

  private static final String DECORATED = "decoratedSession";
  private static final int SEND_TIME_LIMIT_MS = 2000;
  private static final int BUFFER_LIMIT_BYTES = 1024 * 1024;
  private static final int MAX_MESSAGE_CHARS = 8192;

  private final PreviewService previews;
  private final AuthenticatedUserLoader loader;
  private final ObjectMapper objectMapper;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    session
        .getAttributes()
        .put(
            DECORATED,
            new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message)
      throws IOException {
    if (message.getPayload().length() > MAX_MESSAGE_CHARS) {
      return;
    }
    JsonNode json = objectMapper.readTree(message.getPayload());
    switch (json.path("type").asText()) {
      case "SUBSCRIBE" -> subscribe(session, json);
      case "UNSUBSCRIBE" -> previews.unsubscribe(session.getId(), playerIds(json));
      default -> log.debug("Mensaje de vista previa ignorado");
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    previews.close(session.getId());
  }

  /**
   * Suscribe la conexión tras comprobar que su usuario sigue activo y responde con el resultado.
   */
  private void subscribe(WebSocketSession session, JsonNode json) throws IOException {
    AuthenticatedUser stored = (AuthenticatedUser) session.getAttributes().get(USER);
    AuthenticatedUser user = loader.load(stored.id()).orElse(null);
    if (user == null) {
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    WebSocketSession decorated = (WebSocketSession) session.getAttributes().get(DECORATED);
    Integer interval = json.hasNonNull("intervalSec") ? json.get("intervalSec").asInt() : null;
    List<UUID> accepted = previews.subscribe(decorated, user, playerIds(json), interval);
    ObjectNode reply = objectMapper.createObjectNode();
    reply.put("type", "SUBSCRIBED");
    ArrayNode ids = reply.putArray("playerIds");
    accepted.forEach(id -> ids.add(id.toString()));
    decorated.sendMessage(new TextMessage(reply.toString()));
  }

  /** Lee la lista de identificadores de reproductor ignorando los que no sean UUID. */
  private List<UUID> playerIds(JsonNode json) {
    List<UUID> ids = new ArrayList<>();
    for (JsonNode item : json.path("playerIds")) {
      try {
        ids.add(UUID.fromString(item.asText()));
      } catch (IllegalArgumentException e) {
        log.debug("Identificador de reproductor no válido en la vista previa");
      }
    }
    return ids;
  }
}
