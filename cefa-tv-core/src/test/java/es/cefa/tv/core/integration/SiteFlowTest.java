package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Pruebas de las sedes: gestión, permisos por sede, programación por sede y zona horaria. */
class SiteFlowTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "password-1234";

  @BeforeEach
  void authenticate() {
    login();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private ResponseEntity<String> createSiteResponse(String cookie, String name, String timezone) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("timezone", timezone);
    return call(cookie, HttpMethod.POST, "/api/v1/sites", body);
  }

  private String site(String name, String timezone) throws Exception {
    ResponseEntity<String> response = createSiteResponse(sessionCookie, name, timezone);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
    return mapper.readTree(response.getBody()).get("id").asText();
  }

  private ResponseEntity<String> patchPlayer(String cookie, String playerId, Map<String, ?> body) {
    return call(cookie, HttpMethod.PATCH, "/api/v1/players/" + playerId, body);
  }

  private String userWithSiteGrant(List<String> permissions, String siteId, String level)
      throws Exception {
    return userCookie(permissions, List.of(Map.of("siteId", siteId, "level", level)));
  }

  private JsonNode playerIn(String siteId, String name) throws Exception {
    JsonNode enrolled = enroll(name);
    patchPlayer(sessionCookie, enrolled.get("playerId").asText(), Map.of("siteId", siteId));
    return enrolled;
  }

  private Map<String, Object> scheduleFor(String assetId, Map<String, Object> targets) {
    return Map.of(
        "name",
        unique("programacion"),
        "assetId",
        assetId,
        "durationSec",
        10,
        "allDay",
        true,
        "targets",
        targets);
  }

  private JsonNode awaitManifest(String token, Predicate<JsonNode> condition) throws Exception {
    JsonNode manifest = fetchManifest(token);
    for (int i = 0; i < 100 && !condition.test(manifest); i++) {
      Thread.sleep(100);
      manifest = fetchManifest(token);
    }
    return manifest;
  }

  private boolean hasSchedule(JsonNode manifest, String scheduleId) {
    return manifest.get("schedules").findValuesAsText("id").contains(scheduleId);
  }

  @Test
  void onlyAdministratorsManageSitesAndNamesAreUnique() throws Exception {
    String name = unique("Planta");
    ResponseEntity<String> created = createSiteResponse(sessionCookie, name, null);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode site = mapper.readTree(created.getBody());
    assertThat(site.get("timezone").asText()).isEqualTo("Europe/Madrid");
    String id = site.get("id").asText();

    ResponseEntity<String> duplicate = createSiteResponse(sessionCookie, name.toUpperCase(), null);
    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorCode(duplicate)).isEqualTo("SITE_NAME_EXISTS");
    assertThat(errorCode(createSiteResponse(sessionCookie, unique("x"), "Marte/Olimpo")))
        .isEqualTo("INVALID_TIMEZONE");

    Map<String, Object> update = new HashMap<>();
    update.put("name", name + " Norte");
    update.put("city", "Zaragoza");
    update.put("timezone", "Atlantic/Canary");
    ResponseEntity<String> updated =
        call(sessionCookie, HttpMethod.PUT, "/api/v1/sites/" + id, update);
    assertThat(mapper.readTree(updated.getBody()).get("timezone").asText())
        .isEqualTo("Atlantic/Canary");
    assertThat(adminGet("/api/v1/sites/" + id).get("city").asText()).isEqualTo("Zaragoza");
    assertThat(adminGet("/api/v1/sites").findValuesAsText("id")).contains(id);

    String username = unique("prog");
    createUser(username, List.of(schedulerGroup()));
    String scheduler = loginAs(username, PASSWORD);
    ResponseEntity<String> denied = createSiteResponse(scheduler, unique("y"), null);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(denied)).isEqualTo("ADMIN_REQUIRED");
    assertThat(call(scheduler, HttpMethod.PUT, "/api/v1/sites/" + id, update).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(call(scheduler, HttpMethod.DELETE, "/api/v1/sites/" + id, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void playersCarryADescriptionAndASite() throws Exception {
    String siteId = site(unique("Sede"), "Europe/Madrid");
    String other = site(unique("Otra"), "Europe/Madrid");
    String playerId = enroll(unique("pantalla")).get("playerId").asText();

    JsonNode described =
        mapper.readTree(
            patchPlayer(sessionCookie, playerId, Map.of("description", "  Entrada principal  "))
                .getBody());
    assertThat(described.get("description").asText()).isEqualTo("Entrada principal");
    assertThat(
            patchPlayer(sessionCookie, playerId, Map.of("description", "x".repeat(501)))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode assigned =
        mapper.readTree(patchPlayer(sessionCookie, playerId, Map.of("siteId", siteId)).getBody());
    assertThat(assigned.get("siteId").asText()).isEqualTo(siteId);
    assertThat(assigned.get("siteName").asText()).isNotBlank();
    assertThat(adminGet("/api/v1/players?siteId=" + siteId).findValuesAsText("id"))
        .containsExactly(playerId);
    assertThat(adminGet("/api/v1/players?siteId=" + other)).isEmpty();
    assertThat(adminGet("/api/v1/sites/" + siteId).get("playerCount").asInt()).isEqualTo(1);
    assertThat(
            errorCode(
                patchPlayer(
                    sessionCookie, playerId, Map.of("siteId", UUID.randomUUID().toString()))))
        .isEqualTo("NOT_FOUND");

    JsonNode cleared =
        mapper.readTree(
            patchPlayer(sessionCookie, playerId, Map.of("clearSite", true, "description", ""))
                .getBody());
    assertThat(cleared.get("siteId").isNull()).isTrue();
    assertThat(cleared.get("description").isNull()).isTrue();
  }

  @Test
  void deletingASiteNeedsReassignmentAndNoSchedules() throws Exception {
    String siteId = site(unique("Origen"), null);
    String target = site(unique("Destino"), null);
    String playerId = playerIn(siteId, unique("p")).get("playerId").asText();

    ResponseEntity<String> blocked =
        call(sessionCookie, HttpMethod.DELETE, "/api/v1/sites/" + siteId, null);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorCode(blocked)).isEqualTo("SITE_NOT_EMPTY");

    assertThat(
            call(
                    sessionCookie,
                    HttpMethod.DELETE,
                    "/api/v1/sites/" + siteId + "?reassignTo=" + target,
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(adminGet("/api/v1/players/" + playerId).get("siteId").asText()).isEqualTo(target);

    assertThat(
            call(
                    sessionCookie,
                    HttpMethod.DELETE,
                    "/api/v1/sites/" + target + "?reassignTo=none",
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(adminGet("/api/v1/players/" + playerId).get("siteId").isNull()).isTrue();

    String scheduled = site(unique("Programada"), null);
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();
    adminPost("/api/v1/schedules", scheduleFor(assetId, Map.of("siteIds", List.of(scheduled))));
    ResponseEntity<String> inUse =
        call(sessionCookie, HttpMethod.DELETE, "/api/v1/sites/" + scheduled, null);
    assertThat(errorCode(inUse)).isEqualTo("SITE_SCHEDULED");
  }

  @Test
  void siteGrantsScopeWhatAUserSeesAndSchedules() throws Exception {
    String siteA = site(unique("A"), null);
    String siteB = site(unique("B"), null);
    String playerA = playerIn(siteA, unique("pa")).get("playerId").asText();
    String playerB = playerIn(siteB, unique("pb")).get("playerId").asText();
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();
    String scheduler = userWithSiteGrant(SCHEDULER_PERMISSIONS, siteA, "OPERATE");

    List<String> visible =
        mapper
            .readTree(call(scheduler, HttpMethod.GET, "/api/v1/players", null).getBody())
            .findValuesAsText("id");
    assertThat(visible).contains(playerA).doesNotContain(playerB);
    assertThat(call(scheduler, HttpMethod.GET, "/api/v1/players/" + playerB, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    List<String> sites =
        mapper
            .readTree(call(scheduler, HttpMethod.GET, "/api/v1/sites", null).getBody())
            .findValuesAsText("id");
    assertThat(sites).contains(siteA).doesNotContain(siteB);
    assertThat(call(scheduler, HttpMethod.GET, "/api/v1/sites/" + siteB, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(
            call(
                    scheduler,
                    HttpMethod.POST,
                    "/api/v1/schedules",
                    scheduleFor(assetId, Map.of("siteIds", List.of(siteA))))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(
            call(
                    scheduler,
                    HttpMethod.POST,
                    "/api/v1/schedules",
                    scheduleFor(assetId, Map.of("playerIds", List.of(playerA))))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    ResponseEntity<String> foreignSite =
        call(
            scheduler,
            HttpMethod.POST,
            "/api/v1/schedules",
            scheduleFor(assetId, Map.of("siteIds", List.of(siteB))));
    assertThat(foreignSite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(foreignSite)).isEqualTo("SCOPE_DENIED");
    assertThat(
            call(
                    scheduler,
                    HttpMethod.POST,
                    "/api/v1/schedules",
                    scheduleFor(assetId, Map.of("playerIds", List.of(playerB))))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    JsonNode me =
        mapper.readTree(call(scheduler, HttpMethod.GET, "/api/v1/auth/me", null).getBody());
    JsonNode grant = me.get("grants").get(0);
    assertThat(grant.get("siteId").asText()).isEqualTo(siteA);
    assertThat(grant.get("playerGroupId").isNull()).isTrue();
    assertThat(grant.get("level").asText()).isEqualTo("OPERATE");
  }

  @Test
  void aViewGrantOnASiteOnlyReadsAndGroupGrantsStillShowTheSite() throws Exception {
    String siteId = site(unique("Lectura"), null);
    String playerId = playerIn(siteId, unique("p")).get("playerId").asText();
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();
    String reader = userWithSiteGrant(SCHEDULER_PERMISSIONS, siteId, "VIEW");

    assertThat(call(reader, HttpMethod.GET, "/api/v1/players/" + playerId, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call(
                    reader,
                    HttpMethod.POST,
                    "/api/v1/schedules",
                    scheduleFor(assetId, Map.of("siteIds", List.of(siteId))))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    String groupId = adminPost("/api/v1/groups", Map.of("name", unique("g"))).get("id").asText();
    String grouped = enroll(unique("agrupada"), groupId).get("playerId").asText();
    patchPlayer(sessionCookie, grouped, Map.of("siteId", siteId));
    String viaGroup =
        userCookie(
            SCHEDULER_PERMISSIONS, List.of(Map.of("playerGroupId", groupId, "level", "OPERATE")));
    JsonNode sites =
        mapper.readTree(call(viaGroup, HttpMethod.GET, "/api/v1/sites", null).getBody());
    assertThat(sites.findValuesAsText("id")).contains(siteId);
    for (JsonNode candidate : sites) {
      if (siteId.equals(candidate.get("id").asText())) {
        assertThat(candidate.get("playerCount").asInt()).isEqualTo(1);
      }
    }
    assertThat(
            call(
                    viaGroup,
                    HttpMethod.POST,
                    "/api/v1/schedules",
                    scheduleFor(assetId, Map.of("siteIds", List.of(siteId))))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void userGroupsCanGrantSitesAndAGrantNeedsExactlyOneTarget() throws Exception {
    String siteId = site(unique("Equipo"), null);
    String playerId = playerIn(siteId, unique("p")).get("playerId").asText();
    String username = unique("miembro");
    String userId = createUser(username, List.of());
    adminPost(
        "/api/v1/user-groups",
        Map.of(
            "name",
            unique("equipo"),
            "permissions",
            SCHEDULER_PERMISSIONS,
            "memberIds",
            List.of(userId),
            "grants",
            List.of(Map.of("siteId", siteId, "level", "OPERATE"))));
    String cookie = loginAs(username, PASSWORD);
    assertThat(call(cookie, HttpMethod.GET, "/api/v1/players/" + playerId, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    String groupId = adminPost("/api/v1/groups", Map.of("name", unique("g"))).get("id").asText();
    ResponseEntity<String> both =
        call(
            sessionCookie,
            HttpMethod.POST,
            "/api/v1/user-groups",
            Map.of(
                "name",
                unique("malo"),
                "memberIds",
                List.of(),
                "grants",
                List.of(Map.of("siteId", siteId, "playerGroupId", groupId, "level", "VIEW"))));
    assertThat(both.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(both)).isEqualTo("GRANT_TARGET_INVALID");
    ResponseEntity<String> neither =
        call(
            sessionCookie,
            HttpMethod.POST,
            "/api/v1/user-groups",
            Map.of(
                "name",
                unique("vacio"),
                "memberIds",
                List.of(),
                "grants",
                List.of(Map.of("level", "VIEW"))));
    assertThat(errorCode(neither)).isEqualTo("GRANT_TARGET_INVALID");
  }

  @Test
  void schedulingBySiteReachesEveryPlayerOfTheSite() throws Exception {
    String siteId = site(unique("Sede"), null);
    JsonNode first = playerIn(siteId, unique("a"));
    JsonNode second = playerIn(siteId, unique("b"));
    JsonNode outside = enroll(unique("fuera"));
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();

    String scheduleId =
        adminPost("/api/v1/schedules", scheduleFor(assetId, Map.of("siteIds", List.of(siteId))))
            .get("id")
            .asText();

    assertThat(
            hasSchedule(
                awaitManifest(first.get("deviceToken").asText(), m -> hasSchedule(m, scheduleId)),
                scheduleId))
        .isTrue();
    assertThat(
            hasSchedule(
                awaitManifest(second.get("deviceToken").asText(), m -> hasSchedule(m, scheduleId)),
                scheduleId))
        .isTrue();
    Thread.sleep(500);
    assertThat(hasSchedule(fetchManifest(outside.get("deviceToken").asText()), scheduleId))
        .isFalse();

    patchPlayer(sessionCookie, first.get("playerId").asText(), Map.of("clearSite", true));
    JsonNode afterMove =
        awaitManifest(first.get("deviceToken").asText(), m -> !hasSchedule(m, scheduleId));
    assertThat(hasSchedule(afterMove, scheduleId)).isFalse();

    JsonNode schedule = adminGet("/api/v1/schedules/" + scheduleId);
    assertThat(schedule.get("targets").get("siteIds").findValuesAsText("id")).isEmpty();
    assertThat(schedule.get("targets").get("siteIds").get(0).asText()).isEqualTo(siteId);
  }

  @Test
  void theManifestTimezoneFollowsTheSite() throws Exception {
    JsonNode enrolled = enroll(unique("zona"));
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();
    String siteId = site(unique("Mexico"), "America/Mexico_City");

    assertThat(fetchManifest(token).get("timezone").asText()).isEqualTo("Europe/Madrid");

    patchPlayer(sessionCookie, playerId, Map.of("siteId", siteId));
    assertThat(
            awaitManifest(token, m -> "America/Mexico_City".equals(m.get("timezone").asText()))
                .get("timezone")
                .asText())
        .isEqualTo("America/Mexico_City");

    call(
        sessionCookie,
        HttpMethod.PUT,
        "/api/v1/sites/" + siteId,
        Map.of("name", unique("Tokio"), "timezone", "Asia/Tokyo"));
    assertThat(
            awaitManifest(token, m -> "Asia/Tokyo".equals(m.get("timezone").asText()))
                .get("timezone")
                .asText())
        .isEqualTo("Asia/Tokyo");

    patchPlayer(sessionCookie, playerId, Map.of("clearSite", true));
    assertThat(
            awaitManifest(token, m -> "Europe/Madrid".equals(m.get("timezone").asText()))
                .get("timezone")
                .asText())
        .isEqualTo("Europe/Madrid");
  }

  @Test
  void siteChangesAreAudited() throws Exception {
    String name = unique("Auditada");
    String siteId = site(name, null);
    String playerId = enroll(unique("p")).get("playerId").asText();
    patchPlayer(sessionCookie, playerId, Map.of("siteId", siteId));
    call(
        sessionCookie,
        HttpMethod.PUT,
        "/api/v1/sites/" + siteId,
        Map.of("name", name + "-v2", "timezone", "Atlantic/Canary"));
    patchPlayer(sessionCookie, playerId, Map.of("clearSite", true));
    call(sessionCookie, HttpMethod.DELETE, "/api/v1/sites/" + siteId, null);

    List<String> siteActions =
        adminGet("/api/v1/audit?size=50&entityId=" + siteId)
            .get("content")
            .findValuesAsText("action");
    assertThat(siteActions).containsExactlyInAnyOrder("SITE_CREATE", "SITE_UPDATE", "SITE_DELETE");
    JsonNode update =
        adminGet("/api/v1/audit?size=5&action=SITE_UPDATE&entityId=" + siteId)
            .get("content")
            .get(0);
    assertThat(update.get("details").get("changes").findValuesAsText("field"))
        .contains("name", "timezone");
    JsonNode assigned =
        adminGet("/api/v1/audit?size=5&action=PLAYER_SITE_ASSIGN&entityId=" + playerId)
            .get("content");
    assertThat(assigned.size()).isGreaterThanOrEqualTo(1);
  }
}
