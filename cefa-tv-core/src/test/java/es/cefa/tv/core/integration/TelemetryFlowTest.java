package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/** Pruebas de la telemetría, las métricas y las órdenes de pantalla. */
class TelemetryFlowTest extends AbstractIntegrationTest {

  private static final Map<String, Object> FULL_HD =
      Map.of(
          "output",
          "HDMI-1",
          "connected",
          true,
          "primary",
          true,
          "selected",
          true,
          "width",
          1920,
          "height",
          1080,
          "refreshHz",
          60.0,
          "modes",
          List.of("1920x1080", "1280x720"));

  @BeforeEach
  void authenticate() {
    login();
  }

  private Map<String, Object> system(double cpu) {
    Map<String, Object> system = new java.util.HashMap<>();
    system.put("hostname", "pantalla-test");
    system.put("os", "Debian 12");
    system.put("cpuCount", 4);
    system.put("cpuPercent", cpu);
    system.put("ramTotalMb", 8000);
    system.put("ramUsedMb", 1900);
    system.put("diskTotalMb", 200000);
    system.put("diskFreeMb", 150000);
    system.put("tempC", 48.5);
    system.put("shellEnabled", true);
    system.put("shellMode", "container");
    return system;
  }

  private Map<String, Object> heartbeat(
      Map<String, Object> system, List<Map<String, Object>> displays) {
    return Map.of("appliedVersion", 0, "state", "IDLE", "system", system, "displays", displays);
  }

  @Test
  void heartbeatTelemetryIsStoredAndExposed() throws Exception {
    JsonNode enrolled = enroll("pantalla-hb");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();

    playerPost(token, "/player-api/heartbeat", heartbeat(system(12.5), List.of(FULL_HD)));

    JsonNode player = adminGet("/api/v1/players/" + playerId);
    assertThat(player.get("system").get("cpuCount").asInt()).isEqualTo(4);
    assertThat(player.get("system").get("shellEnabled").asBoolean()).isTrue();
    assertThat(player.get("displays")).hasSize(1);
    assertThat(player.get("displays").get(0).get("output").asText()).isEqualTo("HDMI-1");
    assertThat(player.get("resolution").asText()).isEqualTo("1920x1080");

    JsonNode series = adminGet("/api/v1/players/" + playerId + "/metrics?hours=1");
    assertThat(series).hasSize(1);
    assertThat(series.get(0).get("cpuPercent").asDouble()).isEqualTo(12.5);
    assertThat(series.get(0).get("ramUsedMb").asLong()).isEqualTo(1900);
    assertThat(series.get(0).get("diskFreeMb").asLong()).isEqualTo(150000);
    assertThat(series.get(0).get("tempC").asDouble()).isEqualTo(48.5);
  }

  @Test
  void samplesCloserThanTenSecondsAreMerged() throws Exception {
    JsonNode enrolled = enroll("pantalla-throttle");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();

    playerPost(token, "/player-api/heartbeat", heartbeat(system(10), List.of(FULL_HD)));
    playerPost(token, "/player-api/heartbeat", heartbeat(system(90), List.of(FULL_HD)));

    assertThat(adminGet("/api/v1/players/" + playerId + "/metrics")).hasSize(1);
    assertThat(adminGet("/api/v1/players/" + playerId).get("system").get("cpuPercent").asDouble())
        .isEqualTo(90.0);
  }

  @Test
  void telemetryOverWebSocketUpdatesTheResolution() throws Exception {
    JsonNode enrolled = enroll("pantalla-ws-telemetry");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();

    SocketCollector playerSide = new SocketCollector();
    WebSocketSession player =
        new StandardWebSocketClient()
            .execute(
                playerSide,
                new WebSocketHttpHeaders(),
                URI.create(wsUrl("/ws/player?token=" + token)))
            .get(10, TimeUnit.SECONDS);
    Map<String, Object> hd =
        Map.of("output", "DP-1", "connected", true, "selected", true, "width", 1280, "height", 720);
    String message =
        mapper.writeValueAsString(
            Map.of("type", "TELEMETRY", "system", system(33.0), "displays", List.of(hd)));
    player.sendMessage(new TextMessage(message));

    for (int i = 0; i < 50; i++) {
      if ("1280x720".equals(adminGet("/api/v1/players/" + playerId).get("resolution").asText())) {
        break;
      }
      Thread.sleep(100);
    }
    JsonNode stored = adminGet("/api/v1/players/" + playerId);
    assertThat(stored.get("resolution").asText()).isEqualTo("1280x720");
    assertThat(stored.get("system").get("cpuPercent").asDouble()).isEqualTo(33.0);
    player.close();
  }

  @Test
  void displayCommandsAreQueuedForOfflinePlayersAndDeliveredInTheHeartbeat() throws Exception {
    JsonNode enrolled = enroll("pantalla-cmd");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();
    playerPost(token, "/player-api/heartbeat", heartbeat(system(5), List.of(FULL_HD)));

    adminPost("/api/v1/players/" + playerId + "/display/redetect", Map.of());
    adminPost(
        "/api/v1/players/" + playerId + "/display/resolution",
        Map.of("output", "HDMI-1", "width", 1280, "height", 720));

    JsonNode response =
        playerPost(token, "/player-api/heartbeat", heartbeat(system(5), List.of(FULL_HD)));
    JsonNode commands = response.get("commands");
    assertThat(commands).hasSize(2);
    assertThat(commands.get(0).get("type").asText()).isEqualTo("DISPLAY_REDETECT");
    assertThat(commands.get(1).get("type").asText()).isEqualTo("SET_RESOLUTION");
    assertThat(commands.get(1).get("output").asText()).isEqualTo("HDMI-1");
    assertThat(commands.get(1).get("width").asInt()).isEqualTo(1280);
    assertThat(commands.get(1).get("height").asInt()).isEqualTo(720);

    JsonNode again =
        playerPost(token, "/player-api/heartbeat", heartbeat(system(5), List.of(FULL_HD)));
    assertThat(again.get("commands")).isEmpty();
  }

  @Test
  void connectedPlayersReceiveDisplayCommandsImmediately() throws Exception {
    JsonNode enrolled = enroll("pantalla-live");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();

    SocketCollector playerSide = new SocketCollector();
    WebSocketSession player =
        new StandardWebSocketClient()
            .execute(
                playerSide,
                new WebSocketHttpHeaders(),
                URI.create(wsUrl("/ws/player?token=" + token)))
            .get(10, TimeUnit.SECONDS);

    adminPost(
        "/api/v1/players/" + playerId + "/display/resolution",
        Map.of("output", "HDMI-1", "width", 1024, "height", 768));
    JsonNode received = mapper.readTree(playerSide.nextContaining("SET_RESOLUTION"));
    assertThat(received.get("output").asText()).isEqualTo("HDMI-1");
    assertThat(received.get("width").asInt()).isEqualTo(1024);
    assertThat(received.get("height").asInt()).isEqualTo(768);

    adminPost("/api/v1/players/" + playerId + "/display/redetect", Map.of());
    assertThat(playerSide.nextContaining("DISPLAY_REDETECT")).contains("type");
    player.close();
  }

  @Test
  void resolutionForAnUnknownOutputIsRejected() throws Exception {
    JsonNode enrolled = enroll("pantalla-bad-output");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();
    playerPost(token, "/player-api/heartbeat", heartbeat(system(5), List.of(FULL_HD)));

    HttpHeaders headers = adminHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> response =
        rest.exchange(
            url("/api/v1/players/" + playerId + "/display/resolution"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of("output", "VGA-9", "width", 1280, "height", 720), headers),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("DISPLAY_NOT_FOUND");

    ResponseEntity<String> tooSmall =
        rest.exchange(
            url("/api/v1/players/" + playerId + "/display/resolution"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of("output", "HDMI-1", "width", 10, "height", 10), headers),
            String.class);
    assertThat(tooSmall.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void metricsOfAnUnknownPlayerAreNotFound() {
    ResponseEntity<String> response =
        rest.exchange(
            url("/api/v1/players/" + java.util.UUID.randomUUID() + "/metrics"),
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
