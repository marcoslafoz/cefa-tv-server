package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.core.preview.service.PreviewService;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/** Pruebas de la vista previa en vivo: suscripción, retransmisión, caducidad y permisos. */
class PreviewFlowTest extends AbstractIntegrationTest {

  private static final class Viewer extends AbstractWebSocketHandler {
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
      for (int i = 0; i < 10; i++) {
        WebSocketMessage<?> message = messages.poll(10, TimeUnit.SECONDS);
        assertThat(message).as("mensaje del visor").isNotNull();
        if (message instanceof TextMessage text) {
          return text.getPayload();
        }
      }
      throw new AssertionError("No llegó un mensaje de texto");
    }

    byte[] nextBinary() throws Exception {
      for (int i = 0; i < 10; i++) {
        WebSocketMessage<?> message = messages.poll(10, TimeUnit.SECONDS);
        assertThat(message).as("mensaje del visor").isNotNull();
        if (message instanceof BinaryMessage binary) {
          ByteBuffer payload = binary.getPayload();
          byte[] bytes = new byte[payload.remaining()];
          payload.get(bytes);
          return bytes;
        }
      }
      throw new AssertionError("No llegó un fotograma");
    }

    boolean quiet(long millis) throws Exception {
      return messages.poll(millis, TimeUnit.MILLISECONDS) == null;
    }
  }

  @Autowired PreviewService previews;

  private final StandardWebSocketClient client = new StandardWebSocketClient();

  @BeforeEach
  void authenticate() {
    login();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private SocketCollector connectPlayer(String token) throws Exception {
    SocketCollector side = new SocketCollector();
    WebSocketSession player =
        client
            .execute(
                side, new WebSocketHttpHeaders(), URI.create(wsUrl("/ws/player?token=" + token)))
            .get(10, TimeUnit.SECONDS);
    side.session = player;
    player.sendMessage(new TextMessage("{\"type\":\"HELLO\",\"appliedVersion\":0}"));
    side.nextContaining("CONFIG");
    return side;
  }

  private WebSocketSession connectViewer(String cookie, Viewer viewer) throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    if (cookie != null) {
      headers.add(HttpHeaders.COOKIE, cookie);
    }
    return client
        .execute(viewer, headers, URI.create(wsUrl("/ws/previews")))
        .get(10, TimeUnit.SECONDS);
  }

  private void subscribe(WebSocketSession viewer, int interval, String... playerIds)
      throws Exception {
    String ids = String.join(",", Arrays.stream(playerIds).map(id -> "\"" + id + "\"").toList());
    viewer.sendMessage(
        new TextMessage(
            "{\"type\":\"SUBSCRIBE\",\"playerIds\":["
                + ids
                + "],\"intervalSec\":"
                + interval
                + "}"));
  }

  private void sendFrame(WebSocketSession player, byte[] jpeg) throws Exception {
    player.sendMessage(
        new TextMessage(
            "{\"type\":\"PREVIEW_FRAME\",\"w\":320,\"h\":180,\"at\":1,\"jpegBase64\":\""
                + Base64.getEncoder().encodeToString(jpeg)
                + "\"}"));
  }

  private ResponseEntity<byte[]> fetchPreview(String cookie, String playerId) {
    HttpHeaders headers = new HttpHeaders();
    if (cookie != null) {
      headers.add(HttpHeaders.COOKIE, cookie);
    }
    return rest.exchange(
        url("/api/v1/players/" + playerId + "/preview"),
        HttpMethod.GET,
        new HttpEntity<>(headers),
        byte[].class);
  }

  private void assertNoPreviewMessage(SocketCollector side) throws Exception {
    String message;
    while ((message = side.messages.poll(600, TimeUnit.MILLISECONDS)) != null) {
      assertThat(message).doesNotContain("PREVIEW");
    }
  }

  private static byte[] payloadOf(byte[] message) {
    return Arrays.copyOfRange(message, 36, message.length);
  }

  private static String playerIdOf(byte[] message) {
    return new String(message, 0, 36, StandardCharsets.US_ASCII);
  }

  @Test
  void subscribersStartTheCaptureFramesReachThemAndTheLastOneStopsIt() throws Exception {
    JsonNode enrolled = enroll(unique("vista"));
    String playerId = enrolled.get("playerId").asText();
    SocketCollector player = connectPlayer(enrolled.get("deviceToken").asText());
    assertThat(fetchPreview(sessionCookie, playerId).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    Viewer first = new Viewer();
    WebSocketSession firstSession = connectViewer(sessionCookie, first);
    subscribe(firstSession, 5, playerId);
    assertThat(first.nextText()).contains("SUBSCRIBED").contains(playerId);
    assertThat(player.nextContaining("PREVIEW_START"))
        .contains("\"intervalSec\":5")
        .contains("\"maxWidth\":320")
        .contains("\"quality\":45");

    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 9, 8, 7, (byte) 0xFF, (byte) 0xD9};
    sendFrame(player.session, jpeg);
    byte[] received = first.nextBinary();
    assertThat(playerIdOf(received)).isEqualTo(playerId);
    assertThat(payloadOf(received)).isEqualTo(jpeg);

    ResponseEntity<byte[]> cached = fetchPreview(sessionCookie, playerId);
    assertThat(cached.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(cached.getBody()).isEqualTo(jpeg);
    assertThat(cached.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
    assertThat(cached.getHeaders().getCacheControl()).contains("no-store");
    assertThat(cached.getHeaders().getFirst("X-Preview-At")).isNotBlank();
    JsonNode listed = null;
    for (JsonNode candidate : adminGet("/api/v1/players")) {
      if (playerId.equals(candidate.get("id").asText())) {
        listed = candidate;
      }
    }
    assertThat(listed.get("previewAt").isNull()).isFalse();

    sendFrame(player.session, new byte[PreviewService.MAX_FRAME_BYTES + 100]);
    byte[] small = {(byte) 0xFF, (byte) 0xD8, 1};
    sendFrame(player.session, small);
    assertThat(payloadOf(first.nextBinary())).isEqualTo(small);

    Viewer second = new Viewer();
    WebSocketSession secondSession = connectViewer(sessionCookie, second);
    subscribe(secondSession, 10, playerId);
    assertThat(second.nextText()).contains("SUBSCRIBED");
    assertNoPreviewMessage(player);

    firstSession.close();
    assertThat(player.nextContaining("PREVIEW_START")).contains("\"intervalSec\":10");
    secondSession.close();
    assertThat(player.nextContaining("PREVIEW_STOP")).contains("PREVIEW_STOP");
    player.session.close();
  }

  @Test
  void aReconnectingPlayerWithWatchersIsToldToStartAgain() throws Exception {
    JsonNode enrolled = enroll(unique("reconecta"));
    String playerId = enrolled.get("playerId").asText();
    String token = enrolled.get("deviceToken").asText();
    SocketCollector player = connectPlayer(token);
    Viewer viewer = new Viewer();
    WebSocketSession viewerSession = connectViewer(sessionCookie, viewer);
    subscribe(viewerSession, 20, playerId);
    player.nextContaining("PREVIEW_START");
    player.session.close();
    Thread.sleep(300);

    SocketCollector again = connectPlayer(token);

    assertThat(again.nextContaining("PREVIEW_START")).contains("\"intervalSec\":20");
    viewerSession.close();
    again.nextContaining("PREVIEW_STOP");
    again.session.close();
  }

  @Test
  void staleSubscriptionsExpireAndTheViewerIsNotified() throws Exception {
    JsonNode enrolled = enroll(unique("caduca"));
    String playerId = enrolled.get("playerId").asText();
    SocketCollector player = connectPlayer(enrolled.get("deviceToken").asText());
    Viewer viewer = new Viewer();
    WebSocketSession viewerSession = connectViewer(sessionCookie, viewer);
    subscribe(viewerSession, 10, playerId);
    player.nextContaining("PREVIEW_START");

    previews.expireStale(Instant.now().plusSeconds(60));

    player.nextContaining("PREVIEW_STOP");
    assertThat(viewer.nextText()).contains("SUBSCRIBED");
    assertThat(viewer.nextText()).contains("EXPIRED");
    viewerSession.close();
    player.session.close();
  }

  @Test
  void onlyUsersWhoCanViewPlayersConnectAndOnlyVisiblePlayersAreServed() throws Exception {
    String groupId = adminPost("/api/v1/groups", Map.of("name", unique("g"))).get("id").asText();
    JsonNode mine = enroll(unique("mia"), groupId);
    JsonNode foreign = enroll(unique("ajena"));
    String cookie =
        userCookie(
            SCHEDULER_PERMISSIONS, List.of(Map.of("playerGroupId", groupId, "level", "VIEW")));

    SocketCollector foreignSide = connectPlayer(foreign.get("deviceToken").asText());
    Viewer viewer = new Viewer();
    WebSocketSession session = connectViewer(cookie, viewer);
    subscribe(session, 10, mine.get("playerId").asText(), foreign.get("playerId").asText());
    JsonNode reply = mapper.readTree(viewer.nextText());
    assertThat(reply.get("playerIds").findValuesAsText("id")).isEmpty();
    assertThat(reply.get("playerIds")).hasSize(1);
    assertThat(reply.get("playerIds").get(0).asText()).isEqualTo(mine.get("playerId").asText());
    assertNoPreviewMessage(foreignSide);
    session.close();

    String blindCookie = userCookie(List.of("MEDIA_MANAGE"), List.of());
    assertThatThrownBy(() -> connectViewer(blindCookie, new Viewer()))
        .isInstanceOf(ExecutionException.class);
    assertThatThrownBy(() -> connectViewer(null, new Viewer()))
        .isInstanceOf(ExecutionException.class);

    assertThat(fetchPreview(cookie, foreign.get("playerId").asText()).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(fetchPreview(null, mine.get("playerId").asText()).getStatusCode().value())
        .isIn(401, 403);
    foreignSide.session.close();
  }

  @Test
  void playersExposeTheThumbnailOfWhatIsPlaying() throws Exception {
    JsonNode enrolled = enroll(unique("suena"));
    String playerId = enrolled.get("playerId").asText();
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();

    playerPost(
        enrolled.get("deviceToken").asText(),
        "/player-api/heartbeat",
        Map.of("appliedVersion", 0, "state", "PLAYING", "currentAssetId", assetId));

    JsonNode player = adminGet("/api/v1/players/" + playerId);
    assertThat(player.get("currentAssetThumbnailUrl").asText())
        .isEqualTo("/api/v1/media/" + assetId + "/thumbnail");
    assertThat(player.get("previewAt").isNull()).isTrue();
    ResponseEntity<String> missing =
        rest.exchange(
            url("/api/v1/media/" + assetId + "/thumbnail"),
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders()),
            String.class);
    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
