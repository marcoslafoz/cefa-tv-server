package es.cefa.tv.core.shell.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.shell.model.ShellCloseReason;
import es.cefa.tv.core.shell.service.ShellSessionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Gestiona el WebSocket {@code /ws/shell/{playerId}} del navegador con la terminal remota. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShellViewerHandler extends TextWebSocketHandler {

  /** Atributo de sesión con el identificador del reproductor. */
  public static final String PLAYER_ID = "playerId";

  /** Atributo de sesión con el administrador autenticado. */
  public static final String USER = "user";

  private final ShellSessionService shellService;
  private final ObjectMapper objectMapper;

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    UUID playerId = (UUID) session.getAttributes().get(PLAYER_ID);
    JsonNode json = objectMapper.readTree(message.getPayload());
    switch (json.path("type").asText()) {
      case "OPEN" ->
          shellService.open(
              playerId,
              session,
              (AuthenticatedUser) session.getAttributes().get(USER),
              json.path("cols").asInt(0),
              json.path("rows").asInt(0));
      case "INPUT" -> shellService.input(playerId, session.getId(), json.path("data").asText(null));
      case "RESIZE" ->
          shellService.resize(
              playerId, session.getId(), json.path("cols").asInt(0), json.path("rows").asInt(0));
      case "CLOSE" ->
          shellService.closeByViewer(playerId, session.getId(), ShellCloseReason.USER_CLOSE);
      default -> log.debug("Mensaje de terminal ignorado");
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    UUID playerId = (UUID) session.getAttributes().get(PLAYER_ID);
    shellService.closeByViewer(playerId, session.getId(), ShellCloseReason.VIEWER_DISCONNECTED);
  }
}
