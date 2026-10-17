package es.cefa.tv.core.realtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.player.service.PlayerCommandService;
import es.cefa.tv.core.preview.service.PreviewService;
import es.cefa.tv.core.shell.service.ShellSessionService;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/** Gestiona el WebSocket {@code /ws/player} de los reproductores. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerSocketHandler extends AbstractWebSocketHandler {

  /** Atributo de sesión con el identificador del reproductor. */
  public static final String PLAYER_ID = "playerId";

  private static final String SYNC_MESSAGE = "{\"type\":\"SYNC\"}";
  private static final int MAX_PREVIEW_BASE64_CHARS = PreviewService.MAX_FRAME_BYTES * 4 / 3 + 8;

  private final PlayerSessionRegistry registry;
  private final PlayerRealtimeService realtime;
  private final PlayerCommandService commandService;
  private final ShellSessionService shellService;
  private final PreviewService previewService;
  private final ObjectMapper objectMapper;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    UUID playerId = playerId(session);
    registry.registerPlayer(playerId, session);
    realtime.touch(playerId);
    commandService
        .drainPending(playerId)
        .forEach(command -> registry.sendToPlayer(playerId, commandService.toJson(command)));
    String remote = registry.activeRemoteParams(playerId);
    if (remote != null) {
      registry.sendToPlayer(playerId, remote);
    }
    registry.broadcastText(playerId, "{\"type\":\"STATE\",\"online\":true}");
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    UUID playerId = playerId(session);
    JsonNode json = objectMapper.readTree(message.getPayload());
    String type = json.path("type").asText();
    switch (type) {
      case "HELLO" -> {
        boolean outdated = realtime.onHello(playerId, json.path("appliedVersion").asLong(0));
        realtime
            .config(playerId)
            .ifPresent(
                config -> registry.sendToPlayer(playerId, commandService.configJson(config)));
        if (outdated) {
          registry.sendToPlayer(playerId, SYNC_MESSAGE);
        }
        previewService.onPlayerHello(playerId);
      }
      case "STATE" -> {
        realtime.onState(
            playerId,
            json.path("state").asText(null),
            parseUuid(json.path("currentPlaylistId").asText(null)),
            parseUuid(json.path("currentAssetId").asText(null)));
        registry.broadcastText(playerId, message.getPayload());
      }
      case "TELEMETRY" -> {
        realtime.onTelemetry(
            playerId,
            json.hasNonNull("system")
                ? objectMapper.convertValue(
                    json.get("system"), new TypeReference<Map<String, Object>>() {})
                : null,
            json.hasNonNull("displays")
                ? objectMapper.convertValue(
                    json.get("displays"), new TypeReference<List<Map<String, Object>>>() {})
                : null);
        registry.broadcastText(playerId, message.getPayload());
      }
      case "CURSOR" -> registry.broadcastText(playerId, message.getPayload());
      case "PREVIEW_FRAME" -> onPreviewFrame(playerId, json);
      case "SHELL_OUTPUT" ->
          shellService.onPlayerOutput(
              playerId, json.path("sessionId").asText(), json.path("data").asText(null));
      case "SHELL_EXIT" ->
          shellService.onPlayerExit(
              playerId, json.path("sessionId").asText(), json.path("code").asInt(0));
      default -> log.debug("Mensaje de reproductor ignorado: {}", type);
    }
  }

  @Override
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
    registry.broadcastBinary(playerId(session), message.getPayload());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    UUID playerId = playerId(session);
    if (registry.unregisterPlayer(playerId, session.getId())) {
      registry.broadcastText(playerId, "{\"type\":\"STATE\",\"online\":false}");
      shellService.onPlayerDisconnected(playerId);
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.debug(
        "Error de transporte del reproductor {}: {}", playerId(session), exception.getMessage());
  }

  /** Decodifica y entrega el fotograma de vista previa; los demasiado grandes se descartan. */
  private void onPreviewFrame(UUID playerId, JsonNode json) {
    String encoded = json.path("jpegBase64").asText(null);
    if (encoded == null || encoded.length() > MAX_PREVIEW_BASE64_CHARS) {
      previewService.onFrame(playerId, null);
      return;
    }
    try {
      previewService.onFrame(playerId, Base64.getDecoder().decode(encoded));
    } catch (IllegalArgumentException e) {
      previewService.onFrame(playerId, null);
    }
  }

  /** Obtiene el reproductor asociado a la sesión. */
  private UUID playerId(WebSocketSession session) {
    return (UUID) session.getAttributes().get(PLAYER_ID);
  }

  /** Convierte un texto en UUID devolviendo {@code null} si no es válido. */
  private UUID parseUuid(String value) {
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
