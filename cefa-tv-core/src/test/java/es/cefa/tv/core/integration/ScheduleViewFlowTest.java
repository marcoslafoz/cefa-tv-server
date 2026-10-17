package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Pruebas de la versión del reproductor, el estado calculado y el timeline multidestino. */
class ScheduleViewFlowTest extends AbstractIntegrationTest {

  @BeforeEach
  void authenticate() {
    login();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String site(String timezone) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", unique("Sede"));
    body.put("timezone", timezone);
    return adminPost("/api/v1/sites", body).get("id").asText();
  }

  private String group() throws Exception {
    return adminPost("/api/v1/groups", Map.of("name", unique("Grupo"))).get("id").asText();
  }

  private String playerIn(String siteId, String groupId) throws Exception {
    String playerId = enroll(unique("pantalla")).get("playerId").asText();
    Map<String, Object> patch = new HashMap<>();
    if (siteId != null) {
      patch.put("siteId", siteId);
    }
    if (groupId != null) {
      patch.put("groupId", groupId);
    }
    ResponseEntity<String> response =
        call(sessionCookie, HttpMethod.PATCH, "/api/v1/players/" + playerId, patch);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    return playerId;
  }

  private JsonNode createSchedule(String assetId, Map<String, Object> targets, Map<String, ?> extra)
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", unique("programacion"));
    body.put("assetId", assetId);
    body.put("durationSec", 10);
    body.put("allDay", true);
    body.put("targets", targets);
    body.putAll(extra);
    ResponseEntity<String> response =
        call(sessionCookie, HttpMethod.POST, "/api/v1/schedules", body);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
    return mapper.readTree(response.getBody());
  }

  private JsonNode timeline(String cookie, String query) throws Exception {
    LocalDate today = LocalDate.now();
    ResponseEntity<String> response =
        call(
            cookie,
            HttpMethod.GET,
            "/api/v1/schedules/timeline?" + query + "&from=" + today + "&to=" + today,
            null);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    return mapper.readTree(response.getBody());
  }

  private List<String> playerIdsOf(JsonNode occurrences) {
    return StreamSupport.stream(occurrences.spliterator(), false)
        .map(node -> node.get("playerId").asText())
        .distinct()
        .sorted()
        .toList();
  }

  @Test
  void playerBuildComesFromPairingAndFromTelemetry() throws Exception {
    HttpHeaders json = new HttpHeaders();
    json.setContentType(MediaType.APPLICATION_JSON);
    JsonNode requested =
        mapper.readTree(
            rest.exchange(
                    url("/player-api/pairing/request"),
                    HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(
                        Map.of(
                            "hostname",
                            unique("build"),
                            "timezone",
                            "Europe/Madrid",
                            "playerVersion",
                            "0.1.0",
                            "playerBuild",
                            "7578e81"),
                        json),
                    String.class)
                .getBody());
    JsonNode claimed =
        adminPost(
            "/api/v1/pairings/claim",
            Map.of("code", requested.get("code").asText(), "name", unique("pantalla")));
    String playerId = claimed.get("id").asText();

    JsonNode player = adminGet("/api/v1/players/" + playerId);
    assertThat(player.get("playerVersion").asText()).isEqualTo("0.1.0");
    assertThat(player.get("playerBuild").asText()).isEqualTo("7578e81");

    HttpHeaders poll = new HttpHeaders();
    poll.add("X-Pairing-Secret", requested.get("secret").asText());
    JsonNode credentials =
        mapper.readTree(
            rest.exchange(
                    url("/player-api/pairing/" + requested.get("pairingId").asText()),
                    HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(poll),
                    String.class)
                .getBody());
    Map<String, Object> heartbeat = new HashMap<>();
    heartbeat.put("appliedVersion", 0);
    heartbeat.put("state", "IDLE");
    heartbeat.put("system", Map.of("playerVersion", "0.2.0", "playerBuild", "abc1234"));
    playerPost(credentials.get("deviceToken").asText(), "/player-api/heartbeat", heartbeat);

    JsonNode updated = adminGet("/api/v1/players/" + playerId);
    assertThat(updated.get("playerVersion").asText()).isEqualTo("0.2.0");
    assertThat(updated.get("playerBuild").asText()).isEqualTo("abc1234");
  }

  @Test
  void scheduleReportsStatusNextRunAndTheZonesOfItsTargets() throws Exception {
    String madrid = site("Europe/Madrid");
    String tokyo = site("Asia/Tokyo");
    playerIn(madrid, null);
    playerIn(tokyo, null);
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();

    JsonNode both = createSchedule(assetId, Map.of("siteIds", List.of(madrid, tokyo)), Map.of());
    JsonNode onlyTokyo = createSchedule(assetId, Map.of("siteIds", List.of(tokyo)), Map.of());

    assertThat(both.get("status").asText()).isEqualTo("ACTIVE_NOW");
    assertThat(both.get("timezone").isNull()).isTrue();
    assertThat(both.get("timezones"))
        .extracting(JsonNode::asText)
        .containsExactly("Asia/Tokyo", "Europe/Madrid");
    assertThat(both.get("nextRunAt").asText()).isNotBlank();
    assertThat(onlyTokyo.get("timezone").asText()).isEqualTo("Asia/Tokyo");
    assertThat(adminGet("/api/v1/schedules/" + onlyTokyo.get("id").asText()).get("timezones"))
        .hasSize(1);
  }

  @Test
  void statusCoversDisabledEndedAndFutureSchedules() throws Exception {
    String site = site("Europe/Warsaw");
    playerIn(site, null);
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();
    Map<String, Object> targets = Map.of("siteIds", List.of(site));
    LocalDate today = LocalDate.now();

    JsonNode disabled = createSchedule(assetId, targets, Map.of("enabled", false));
    JsonNode ended =
        createSchedule(assetId, targets, Map.of("endDate", today.minusDays(3).toString()));
    JsonNode future =
        createSchedule(assetId, targets, Map.of("startDate", today.plusDays(10).toString()));

    assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");
    assertThat(disabled.get("nextRunAt").isNull()).isTrue();
    assertThat(ended.get("status").asText()).isEqualTo("ENDED");
    assertThat(ended.get("nextRunAt").isNull()).isTrue();
    assertThat(future.get("status").asText()).isEqualTo("SCHEDULED");
    assertThat(future.get("nextRunAt").asText()).isNotBlank();
  }

  @Test
  void timelineAcceptsSeveralTargetsInOneRequest() throws Exception {
    String groupId = group();
    String siteId = site("Asia/Tokyo");
    String inGroup = playerIn(null, groupId);
    String inSite = playerIn(siteId, null);
    String alone = playerIn(null, null);
    String unrelated = playerIn(null, null);
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();
    createSchedule(
        assetId,
        Map.of(
            "playerIds", List.of(alone), "groupIds", List.of(groupId), "siteIds", List.of(siteId)),
        Map.of());

    JsonNode occurrences =
        timeline(
            sessionCookie,
            "groupIds="
                + groupId
                + "&siteIds="
                + siteId
                + "&playerIds="
                + alone
                + "&playerIds="
                + unrelated);

    assertThat(playerIdsOf(occurrences)).containsExactlyInAnyOrder(inGroup, inSite, alone);
    JsonNode tokyoOccurrence =
        StreamSupport.stream(occurrences.spliterator(), false)
            .filter(node -> node.get("playerId").asText().equals(inSite))
            .findFirst()
            .orElseThrow();
    assertThat(tokyoOccurrence.get("timezone").asText()).isEqualTo("Asia/Tokyo");
    LocalDate queryDay = LocalDate.now();
    LocalDate tokyoToday = LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"));
    String expectedStatus =
        queryDay.isBefore(tokyoToday)
            ? "ENDED"
            : queryDay.isAfter(tokyoToday) ? "SCHEDULED" : "ACTIVE_NOW";
    assertThat(tokyoOccurrence.get("status").asText()).isEqualTo(expectedStatus);
    assertThat(tokyoOccurrence.get("playerName").asText()).isNotBlank();
    assertThat(tokyoOccurrence.get("nextRunAt").asText()).isNotBlank();

    assertThat(playerIdsOf(timeline(sessionCookie, "playerIds=" + alone + "," + inSite)))
        .containsExactlyInAnyOrder(alone, inSite);
    assertThat(playerIdsOf(timeline(sessionCookie, "playerId=" + inGroup)))
        .containsExactly(inGroup);
  }

  @Test
  void timelineRespectsPermissionsAndLimits() throws Exception {
    String visibleSite = site("Europe/Madrid");
    String hiddenSite = site("Europe/Madrid");
    String visible = playerIn(visibleSite, null);
    String hidden = playerIn(hiddenSite, null);
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();
    createSchedule(assetId, Map.of("siteIds", List.of(visibleSite, hiddenSite)), Map.of());
    String viewer =
        userCookie(SCHEDULER_PERMISSIONS, List.of(Map.of("siteId", visibleSite, "level", "VIEW")));

    JsonNode seen =
        timeline(
            viewer, "siteIds=" + visibleSite + "&siteIds=" + hiddenSite + "&playerIds=" + hidden);
    assertThat(playerIdsOf(seen)).containsExactly(visible);

    LocalDate today = LocalDate.now();
    ResponseEntity<String> forbidden =
        call(
            viewer,
            HttpMethod.GET,
            "/api/v1/schedules/timeline?playerId=" + hidden + "&from=" + today + "&to=" + today,
            null);
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> noTargets =
        call(
            sessionCookie,
            HttpMethod.GET,
            "/api/v1/schedules/timeline?from=" + today + "&to=" + today,
            null);
    assertThat(noTargets.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(noTargets)).isEqualTo("TARGETS_REQUIRED");

    ResponseEntity<String> tooWide =
        call(
            sessionCookie,
            HttpMethod.GET,
            "/api/v1/schedules/timeline?playerId="
                + visible
                + "&from="
                + today
                + "&to="
                + today.plusDays(120),
            null);
    assertThat(tooWide.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(tooWide)).isEqualTo("INVALID_RANGE");
  }
}
