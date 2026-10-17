package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.core.pairing.service.PairingService;
import es.cefa.tv.core.player.service.PlayerNeverConnectedCleaner;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/**
 * Pruebas de playlists ocultas, permisos del contenido por defecto, emparejamientos huérfanos,
 * selección de salida de vídeo y miniaturas de las programaciones.
 */
class PlayerFixesTest extends AbstractIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired PairingService pairingService;
  @Autowired PlayerNeverConnectedCleaner cleaner;

  private final StandardWebSocketClient client = new StandardWebSocketClient();

  @BeforeEach
  void authenticate() {
    login();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String playlist(String name, boolean hidden) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("items", List.of());
    body.put("hidden", hidden);
    return adminPost("/api/v1/playlists", body).get("id").asText();
  }

  private List<String> playlistIds(String query) throws Exception {
    return adminGet("/api/v1/playlists" + query).findValuesAsText("id");
  }

  private String userWith(List<String> permissions, String groupId, String level) throws Exception {
    return userCookie(permissions, List.of(Map.of("playerGroupId", groupId, "level", level)));
  }

  private ResponseEntity<String> patchPlayer(String cookie, String playerId, Map<String, ?> body) {
    return call(cookie, HttpMethod.PATCH, "/api/v1/players/" + playerId, body);
  }

  @Test
  void hiddenPlaylistsAreOmittedUnlessRequested() throws Exception {
    String visible = playlist(unique("visible"), false);
    String hidden = playlist(unique("oculta"), true);

    assertThat(playlistIds("")).contains(visible).doesNotContain(hidden);
    assertThat(playlistIds("?includeHidden=true")).contains(visible, hidden);

    JsonNode renamed =
        mapper.readTree(
            call(
                    sessionCookie,
                    HttpMethod.PUT,
                    "/api/v1/playlists/" + hidden,
                    Map.of("name", unique("sigue"), "items", List.of()))
                .getBody());
    assertThat(renamed.get("hidden").asBoolean()).isTrue();
  }

  @Test
  void settingTheDefaultContentNeedsSchedulingPermissionNotPlayerManagement() throws Exception {
    String groupName = unique("grupo");
    String groupId = adminPost("/api/v1/groups", Map.of("name", groupName)).get("id").asText();
    String playerName = unique("pantalla");
    String playerId = enroll(playerName, groupId).get("playerId").asText();
    String playlistId = playlist(unique("reposo"), false);

    String scheduler = userWith(SCHEDULER_PERMISSIONS, groupId, "OPERATE");
    assertThat(
            patchPlayer(scheduler, playerId, Map.of("fallbackPlaylistId", playlistId))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    ResponseEntity<String> rename =
        patchPlayer(scheduler, playerId, Map.of("name", unique("otro")));
    assertThat(rename.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(rename)).isEqualTo("PERMISSION_DENIED");
    assertThat(
            patchPlayer(
                    scheduler,
                    playerId,
                    Map.of("name", playerName, "fallbackPlaylistId", playlistId))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    assertThat(
            call(
                    scheduler,
                    HttpMethod.PUT,
                    "/api/v1/groups/" + groupId,
                    Map.of("name", groupName, "fallbackPlaylistId", playlistId))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call(
                    scheduler,
                    HttpMethod.PUT,
                    "/api/v1/groups/" + groupId,
                    Map.of("name", unique("renombrado"), "fallbackPlaylistId", playlistId))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    String manager = userWith(List.of("PLAYER_VIEW", "PLAYER_MANAGE"), groupId, "OPERATE");
    ResponseEntity<String> noScheduling =
        patchPlayer(manager, playerId, Map.of("fallbackPlaylistId", playlistId));
    assertThat(noScheduling.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(patchPlayer(manager, playerId, Map.of("orientation", "VERTICAL")).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    String viewer = userWith(VIEWER_PERMISSIONS, groupId, "VIEW");
    assertThat(
            patchPlayer(viewer, playerId, Map.of("fallbackPlaylistId", playlistId)).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  private Map<String, String> requestPairing() throws Exception {
    HttpHeaders json = new HttpHeaders();
    json.setContentType(MediaType.APPLICATION_JSON);
    JsonNode requested =
        mapper.readTree(
            rest.exchange(
                    url("/player-api/pairing/request"),
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("hostname", unique("host")), json),
                    String.class)
                .getBody());
    return Map.of(
        "id", requested.get("pairingId").asText(),
        "secret", requested.get("secret").asText(),
        "code", requested.get("code").asText());
  }

  private JsonNode pollPairing(Map<String, String> pairing) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Pairing-Secret", pairing.get("secret"));
    return mapper.readTree(
        rest.exchange(
                url("/player-api/pairing/" + pairing.get("id")),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class)
            .getBody());
  }

  private String claim(Map<String, String> pairing) throws Exception {
    return adminPost(
            "/api/v1/pairings/claim", Map.of("code", pairing.get("code"), "name", unique("p")))
        .get("id")
        .asText();
  }

  @Test
  void anApprovedPairingKeepsDeliveringCredentialsAfterTheCodeExpired() throws Exception {
    Map<String, String> pairing = requestPairing();
    String playerId = claim(pairing);
    jdbc.update(
        "update pairings set expires_at = now() - interval '1 hour',"
            + " created_at = now() - interval '20 hours' where id = ?::uuid",
        pairing.get("id"));
    pairingService.cleanup();

    JsonNode status = pollPairing(pairing);
    assertThat(status.get("status").asText()).isEqualTo("APPROVED");
    assertThat(status.get("playerId").asText()).isEqualTo(playerId);
    assertThat(status.get("deviceToken").asText()).isNotBlank();

    jdbc.update(
        "update pairings set approved_at = now() - interval '25 hours' where id = ?::uuid",
        pairing.get("id"));
    pairingService.cleanup();
    assertThat(pollPairing(pairing).get("status").asText()).isEqualTo("EXPIRED");
  }

  @Test
  void playersThatNeverConnectedAreFlaggedAndEventuallyRemoved() throws Exception {
    JsonNode orphan = enroll(unique("huerfana"));
    String orphanId = orphan.get("playerId").asText();
    JsonNode active = enroll(unique("activa"));
    String activeId = active.get("playerId").asText();
    playerPost(
        active.get("deviceToken").asText(),
        "/player-api/heartbeat",
        Map.of("appliedVersion", 0, "state", "IDLE"));

    assertThat(adminGet("/api/v1/players/" + orphanId).get("neverConnected").asBoolean()).isFalse();
    jdbc.update("update players set created_at = now() - interval '8 days'");
    assertThat(adminGet("/api/v1/players/" + orphanId).get("neverConnected").asBoolean()).isTrue();
    assertThat(adminGet("/api/v1/players/" + activeId).get("neverConnected").asBoolean()).isFalse();

    jdbc.update("update players set created_at = now() - interval '20 days'");
    cleaner.clean();

    assertThat(
            call(sessionCookie, HttpMethod.GET, "/api/v1/players/" + orphanId, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(sessionCookie, HttpMethod.GET, "/api/v1/players/" + activeId, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    JsonNode removed =
        adminGet("/api/v1/audit?action=PLAYER_AUTO_DELETE&entityId=" + orphanId)
            .get("content")
            .get(0);
    assertThat(removed.get("username").asText()).isEqualTo("system");
  }

  private Map<String, Object> display(String output, boolean connected) {
    return Map.of(
        "output",
        output,
        "connected",
        connected,
        "selected",
        connected,
        "width",
        1920,
        "height",
        1080,
        "modes",
        List.of("1920x1080"));
  }

  @Test
  void thePreferredOutputIsValidatedAuditedAndDeliveredToThePlayer() throws Exception {
    JsonNode enrolled = enroll(unique("salida"));
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();

    ResponseEntity<String> free =
        patchPlayer(sessionCookie, playerId, Map.of("preferredOutput", "DP-9"));
    assertThat(free.getStatusCode()).isEqualTo(HttpStatus.OK);
    patchPlayer(sessionCookie, playerId, Map.of("clearPreferredOutput", true));

    playerPost(
        token,
        "/player-api/heartbeat",
        Map.of(
            "appliedVersion",
            0,
            "state",
            "IDLE",
            "displays",
            List.of(display("HDMI-1", true), display("HDMI-2", true), display("DP-1", false))));

    ResponseEntity<String> unknown =
        patchPlayer(sessionCookie, playerId, Map.of("preferredOutput", "DP-9"));
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(unknown)).isEqualTo("DISPLAY_NOT_FOUND");
    assertThat(
            patchPlayer(sessionCookie, playerId, Map.of("preferredOutput", "DP-1")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    SocketCollector playerSide = new SocketCollector();
    WebSocketSession player =
        client
            .execute(
                playerSide,
                new WebSocketHttpHeaders(),
                URI.create(wsUrl("/ws/player?token=" + token)))
            .get(10, TimeUnit.SECONDS);
    player.sendMessage(new TextMessage("{\"type\":\"HELLO\",\"appliedVersion\":0}"));
    assertThat(playerSide.nextContaining("CONFIG")).contains("\"preferredOutput\":null");

    JsonNode patched =
        mapper.readTree(
            patchPlayer(sessionCookie, playerId, Map.of("preferredOutput", "HDMI-2")).getBody());
    assertThat(patched.get("preferredOutput").asText()).isEqualTo("HDMI-2");
    assertThat(playerSide.nextContaining("CONFIG")).contains("\"preferredOutput\":\"HDMI-2\"");

    JsonNode heartbeat =
        playerPost(token, "/player-api/heartbeat", Map.of("appliedVersion", 0, "state", "IDLE"));
    assertThat(heartbeat.get("config").get("preferredOutput").asText()).isEqualTo("HDMI-2");

    JsonNode audit =
        adminGet("/api/v1/audit?action=PLAYER_OUTPUT_CHANGE&entityId=" + playerId).get("content");
    assertThat(audit.size()).isGreaterThanOrEqualTo(1);
    JsonNode change = audit.get(0).get("details").get("changes").get(0);
    assertThat(change.get("field").asText()).isEqualTo("preferredOutput");
    assertThat(change.get("after").asText()).isEqualTo("HDMI-2");

    patchPlayer(sessionCookie, playerId, Map.of("clearPreferredOutput", true));
    assertThat(playerSide.nextContaining("CONFIG")).contains("\"preferredOutput\":null");
    assertThat(adminGet("/api/v1/players/" + playerId).get("preferredOutput").isNull()).isTrue();
    player.close();
  }

  @Test
  void onlyPlayerManagersCanChangeTheOutput() throws Exception {
    String groupId = adminPost("/api/v1/groups", Map.of("name", unique("g"))).get("id").asText();
    String playerId = enroll(unique("p"), groupId).get("playerId").asText();
    String scheduler = userWith(SCHEDULER_PERMISSIONS, groupId, "OPERATE");

    ResponseEntity<String> denied =
        patchPlayer(scheduler, playerId, Map.of("preferredOutput", "HDMI-1"));

    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void schedulesExposeThumbnailsAndTotals() throws Exception {
    String playerId = enroll(unique("miniaturas")).get("playerId").asText();
    String image = uploadImage(unique("img") + ".png").get("id").asText();
    String web =
        adminPost(
                "/api/v1/media/web",
                Map.of("name", unique("web"), "url", "https://example.org", "refreshSec", 60))
            .get("id")
            .asText();
    List<Map<String, Object>> items = new java.util.ArrayList<>();
    items.add(Map.of("assetId", image, "durationSec", 10, "fit", "COVER", "muted", true));
    items.add(Map.of("assetId", web, "durationSec", 20, "fit", "COVER", "muted", true));
    for (int i = 0; i < 9; i++) {
      items.add(Map.of("assetId", image, "durationSec", 5, "fit", "COVER", "muted", true));
    }
    String playlistId =
        adminPost("/api/v1/playlists", Map.of("name", unique("lista"), "items", items))
            .get("id")
            .asText();
    String scheduleId =
        adminPost(
                "/api/v1/schedules",
                Map.of(
                    "name",
                    unique("prog"),
                    "playlistId",
                    playlistId,
                    "allDay",
                    true,
                    "targets",
                    Map.of("playerIds", List.of(playerId), "groupIds", List.of())))
            .get("id")
            .asText();

    JsonNode one = adminGet("/api/v1/schedules/" + scheduleId);
    assertThat(one.get("thumbnailUrls")).hasSize(8);
    assertThat(one.get("thumbnailUrls").get(0).asText())
        .isEqualTo("/api/v1/media/" + image + "/thumbnail");
    assertThat(one.get("thumbnailUrls").get(1).isNull()).isTrue();
    assertThat(one.get("itemCount").asInt()).isEqualTo(11);
    assertThat(one.get("totalDurationSec").asInt()).isEqualTo(10 + 20 + 9 * 5);

    JsonNode listed = null;
    for (JsonNode candidate : adminGet("/api/v1/schedules")) {
      if (scheduleId.equals(candidate.get("id").asText())) {
        listed = candidate;
      }
    }
    assertThat(listed).isNotNull();
    assertThat(listed.get("thumbnailUrls")).hasSize(8);

    LocalDate today = LocalDate.now();
    JsonNode timeline =
        adminGet(
            "/api/v1/schedules/timeline?playerId=" + playerId + "&from=" + today + "&to=" + today);
    assertThat(timeline).hasSize(1);
    assertThat(timeline.get(0).get("thumbnailUrls")).hasSize(8);
    assertThat(timeline.get(0).get("itemCount").asInt()).isEqualTo(11);
    assertThat(timeline.get(0).get("totalDurationSec").asInt()).isEqualTo(75);
  }
}
