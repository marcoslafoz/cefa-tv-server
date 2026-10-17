package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/** Prueba del relé WebSocket entre el navegador de administración y el reproductor. */
class RemoteControlTest extends AbstractIntegrationTest {

  private static final class Collector extends AbstractWebSocketHandler {
    final BlockingQueue<WebSocketMessage<?>> messages = new LinkedBlockingQueue<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
      messages.add(message);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
      messages.add(message);
    }

    String nextText() throws Exception {
      WebSocketMessage<?> message = messages.poll(10, TimeUnit.SECONDS);
      assertThat(message).isNotNull();
      return message.getPayload().toString();
    }

    WebSocketMessage<?> nextOfType(Class<?> type) throws Exception {
      for (int i = 0; i < 10; i++) {
        WebSocketMessage<?> message = messages.poll(10, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        if (type.isInstance(message)) {
          return message;
        }
      }
      throw new AssertionError("No llegó un mensaje de tipo " + type);
    }
  }

  @BeforeEach
  void authenticate() {
    login();
  }

  @Test
  void viewerInputReachesThePlayerAndFramesComeBack() throws Exception {
    JsonNode enrolled = enroll("pantalla-ws");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();

    StandardWebSocketClient client = new StandardWebSocketClient();
    Collector playerSide = new Collector();
    WebSocketSession player =
        client
            .execute(
                playerSide,
                new WebSocketHttpHeaders(),
                URI.create(wsUrl("/ws/player?token=" + token)))
            .get(10, TimeUnit.SECONDS);
    player.sendMessage(new TextMessage("{\"type\":\"HELLO\",\"appliedVersion\":0}"));
    assertThat(playerSide.nextText()).contains("CONFIG");
    assertThat(playerSide.nextText()).contains("SYNC");

    WebSocketHttpHeaders viewerHeaders = new WebSocketHttpHeaders();
    viewerHeaders.add(HttpHeaders.COOKIE, sessionCookie);
    Collector viewerSide = new Collector();
    WebSocketSession viewer =
        client
            .execute(viewerSide, viewerHeaders, URI.create(wsUrl("/ws/remote/" + playerId)))
            .get(10, TimeUnit.SECONDS);
    assertThat(viewerSide.nextText()).contains("\"online\":true");

    viewer.sendMessage(new TextMessage("{\"type\":\"START\",\"fps\":5}"));
    assertThat(playerSide.nextText()).contains("REMOTE_START").contains("\"fps\":5");

    viewer.sendMessage(
        new TextMessage(
            "{\"type\":\"INPUT\",\"kind\":\"MOUSE_DOWN\",\"x\":0.5,\"y\":0.25,\"button\":\"left\"}"));
    assertThat(playerSide.nextText()).contains("MOUSE_DOWN").contains("0.25");

    viewer.sendMessage(
        new TextMessage(
            "{\"type\":\"INPUT\",\"kind\":\"TEXT\",\"text\":\"p\u00e1ssw\u00f6rd@#\u20ac\"}"));
    assertThat(playerSide.nextText()).contains("TEXT").contains("@#");

    viewer.sendMessage(
        new TextMessage(
            "{\"type\":\"INPUT\",\"kind\":\"KEY_DOWN\",\"key\":\"Enter\",\"code\":\"Enter\",\"windowsVirtualKeyCode\":13,\"modifiers\":0}"));
    assertThat(playerSide.nextText()).contains("KEY_DOWN").contains("windowsVirtualKeyCode");

    viewer.sendMessage(new TextMessage("{\"type\":\"INPUT\",\"kind\":\"FORMAT_DISK\"}"));
    viewer.sendMessage(
        new TextMessage("{\"type\":\"INPUT\",\"kind\":\"TEXT\",\"text\":\"despues\"}"));
    assertThat(playerSide.nextText()).contains("despues");

    player.sendMessage(new TextMessage("{\"type\":\"CURSOR\",\"css\":\"pointer\"}"));
    assertThat(viewerSide.nextText()).contains("CURSOR").contains("pointer");

    byte[] frame = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
    player.sendMessage(new BinaryMessage(frame));
    BinaryMessage received = (BinaryMessage) viewerSide.nextOfType(BinaryMessage.class);
    ByteBuffer payload = received.getPayload();
    assertThat(payload.remaining()).isEqualTo(frame.length);

    viewer.close();
    assertThat(playerSide.nextText()).contains("REMOTE_STOP");

    HttpEntity<Void> delete = new HttpEntity<>(adminHeaders());
    rest.exchange(url("/api/v1/players/" + playerId), HttpMethod.DELETE, delete, String.class);
    assertThat(playerSide.nextText()).contains("UNENROLL");
    assertThat(adminGet("/api/v1/players").findValuesAsText("id")).doesNotContain(playerId);
    assertThat(List.of(UUID.fromString(playerId))).hasSize(1);
  }
}
