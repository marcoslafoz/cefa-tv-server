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

/** Pruebas de la sede de los grupos de reproductores y su herencia en los reproductores. */
class GroupSiteFlowTest extends AbstractIntegrationTest {

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

  private ResponseEntity<String> createGroup(String cookie, Map<String, Object> body) {
    return call(cookie, HttpMethod.POST, "/api/v1/groups", body);
  }

  private String group(String siteId) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", unique("Grupo"));
    if (siteId != null) {
      body.put("siteId", siteId);
    }
    ResponseEntity<String> response = createGroup(sessionCookie, body);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
    return mapper.readTree(response.getBody()).get("id").asText();
  }

  private ResponseEntity<String> updateGroup(
      String cookie, String id, String name, Map<String, Object> extra) {
    Map<String, Object> body = new HashMap<>(extra);
    body.put("name", name);
    return call(cookie, HttpMethod.PUT, "/api/v1/groups/" + id, body);
  }

  private JsonNode groupOf(String id) throws Exception {
    for (JsonNode group : adminGet("/api/v1/groups")) {
      if (group.get("id").asText().equals(id)) {
        return group;
      }
    }
    throw new AssertionError("Grupo no encontrado " + id);
  }

  private String playerIn(String groupId) throws Exception {
    return enroll(unique("pantalla"), groupId).get("playerId").asText();
  }

  private ResponseEntity<String> patchPlayer(String cookie, String id, Map<String, ?> body) {
    return call(cookie, HttpMethod.PATCH, "/api/v1/players/" + id, body);
  }

  private String siteOfPlayer(String id) throws Exception {
    JsonNode site = adminGet("/api/v1/players/" + id).get("siteId");
    return site.isNull() ? null : site.asText();
  }

  private JsonNode awaitManifest(String token, Predicate<JsonNode> condition) throws Exception {
    JsonNode manifest = fetchManifest(token);
    for (int i = 0; i < 100 && !condition.test(manifest); i++) {
      Thread.sleep(100);
      manifest = fetchManifest(token);
    }
    return manifest;
  }

  @Test
  void assigningASiteToAGroupMovesAllItsPlayers() throws Exception {
    String siteId = site("Europe/Madrid");
    String groupId = group(null);
    String first = playerIn(groupId);
    String second = playerIn(groupId);
    String outsider = playerIn(null);

    ResponseEntity<String> response =
        updateGroup(
            sessionCookie,
            groupId,
            groupOf(groupId).get("name").asText(),
            Map.of("siteId", siteId));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode updated = mapper.readTree(response.getBody());
    assertThat(updated.get("siteId").asText()).isEqualTo(siteId);
    assertThat(updated.get("siteName").asText()).isNotBlank();
    assertThat(siteOfPlayer(first)).isEqualTo(siteId);
    assertThat(siteOfPlayer(second)).isEqualTo(siteId);
    assertThat(siteOfPlayer(outsider)).isNull();

    JsonNode groupAudit =
        adminGet("/api/v1/audit?size=50&action=GROUP_SITE_ASSIGN&entityId=" + groupId)
            .get("content");
    assertThat(groupAudit).hasSize(1);
    assertThat(groupAudit.get(0).get("details").get("affectedPlayers").asInt()).isEqualTo(2);
    JsonNode playerAudit =
        adminGet("/api/v1/audit?size=50&action=PLAYER_SITE_ASSIGN&entityId=" + first)
            .get("content");
    assertThat(playerAudit).isNotEmpty();
  }

  @Test
  void aGroupCreatedWithASiteAndUnknownSitesAreValidated() throws Exception {
    String siteId = site("Europe/Madrid");
    String groupId = group(siteId);
    assertThat(groupOf(groupId).get("siteId").asText()).isEqualTo(siteId);

    Map<String, Object> body = new HashMap<>();
    body.put("name", unique("Grupo"));
    body.put("siteId", UUID.randomUUID().toString());
    ResponseEntity<String> unknown = createGroup(sessionCookie, body);
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(unknown)).isEqualTo("SITE_NOT_FOUND");
  }

  @Test
  void aPlayerEnteringAGroupInheritsItsSite() throws Exception {
    String siteId = site("Europe/Madrid");
    String groupId = group(siteId);
    String claimed = playerIn(groupId);
    String moved = playerIn(null);

    assertThat(siteOfPlayer(claimed)).isEqualTo(siteId);
    assertThat(siteOfPlayer(moved)).isNull();

    ResponseEntity<String> response = patchPlayer(sessionCookie, moved, Map.of("groupId", groupId));
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(siteOfPlayer(moved)).isEqualTo(siteId);
  }

  @Test
  void aDifferentSiteIsRejectedWhileTheGroupHasOne() throws Exception {
    String siteId = site("Europe/Madrid");
    String other = site("Asia/Tokyo");
    String groupId = group(siteId);
    String groupName = groupOf(groupId).get("name").asText();
    String playerId = playerIn(groupId);

    ResponseEntity<String> conflict = patchPlayer(sessionCookie, playerId, Map.of("siteId", other));
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorCode(conflict)).isEqualTo("SITE_INHERITED_FROM_GROUP");
    assertThat(mapper.readTree(conflict.getBody()).get("message").asText()).contains(groupName);

    ResponseEntity<String> cleared =
        patchPlayer(sessionCookie, playerId, Map.of("clearSite", true));
    assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorCode(cleared)).isEqualTo("SITE_INHERITED_FROM_GROUP");

    ResponseEntity<String> same = patchPlayer(sessionCookie, playerId, Map.of("siteId", siteId));
    assertThat(same.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(siteOfPlayer(playerId)).isEqualTo(siteId);
  }

  @Test
  void movingToAGroupWithASiteAndPassingItsSiteInOneRequestIsAccepted() throws Exception {
    String siteId = site("Europe/Madrid");
    String groupId = group(siteId);
    String playerId = playerIn(null);

    ResponseEntity<String> response =
        patchPlayer(sessionCookie, playerId, Map.of("groupId", groupId, "siteId", siteId));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(siteOfPlayer(playerId)).isEqualTo(siteId);
  }

  @Test
  void removingTheGroupSiteKeepsThePlayersSiteAndAllowsEditingIt() throws Exception {
    String siteId = site("Europe/Madrid");
    String other = site("Asia/Tokyo");
    String groupId = group(siteId);
    String name = groupOf(groupId).get("name").asText();
    String playerId = playerIn(groupId);

    ResponseEntity<String> response =
        updateGroup(sessionCookie, groupId, name, Map.of("clearSite", true));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(mapper.readTree(response.getBody()).get("siteId").isNull()).isTrue();
    assertThat(siteOfPlayer(playerId)).isEqualTo(siteId);
    ResponseEntity<String> edit = patchPlayer(sessionCookie, playerId, Map.of("siteId", other));
    assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(siteOfPlayer(playerId)).isEqualTo(other);
  }

  @Test
  void leavingTheGroupKeepsTheSite() throws Exception {
    String siteId = site("Europe/Madrid");
    String groupId = group(siteId);
    String playerId = playerIn(groupId);

    ResponseEntity<String> response =
        patchPlayer(sessionCookie, playerId, Map.of("clearGroup", true));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(adminGet("/api/v1/players/" + playerId).get("groupId").isNull()).isTrue();
    assertThat(siteOfPlayer(playerId)).isEqualTo(siteId);
  }

  @Test
  void theManifestTimezoneFollowsTheGroupSite() throws Exception {
    String tokyo = site("Asia/Tokyo");
    String groupId = group(null);
    JsonNode enrolled = enroll(unique("zona"), groupId);
    String token = enrolled.get("deviceToken").asText();
    assertThat(fetchManifest(token).get("timezone").asText()).isEqualTo("Europe/Madrid");

    updateGroup(
        sessionCookie, groupId, groupOf(groupId).get("name").asText(), Map.of("siteId", tokyo));

    assertThat(
            awaitManifest(token, m -> "Asia/Tokyo".equals(m.get("timezone").asText()))
                .get("timezone")
                .asText())
        .isEqualTo("Asia/Tokyo");
  }

  @Test
  void onlyAdministratorsChangeTheSiteOfAGroup() throws Exception {
    String siteId = site("Europe/Madrid");
    String groupId = group(null);
    String name = groupOf(groupId).get("name").asText();
    String scheduler =
        userCookie(
            List.of("PLAYER_MANAGE", "SCHEDULE_MANAGE", "PLAYER_VIEW"),
            List.of(Map.of("playerGroupId", groupId, "level", "OPERATE")));

    ResponseEntity<String> denied = updateGroup(scheduler, groupId, name, Map.of("siteId", siteId));

    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(denied)).isEqualTo("ADMIN_REQUIRED");
    assertThat(groupOf(groupId).get("siteId").isNull()).isTrue();
    ResponseEntity<String> sameSite = updateGroup(scheduler, groupId, name, Map.of());
    assertThat(sameSite.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void deletingASiteWithGroupsNeedsAReassignment() throws Exception {
    String siteId = site("Europe/Madrid");
    String other = site("Europe/Warsaw");
    String groupId = group(siteId);
    String playerId = playerIn(groupId);

    ResponseEntity<String> blocked =
        call(sessionCookie, HttpMethod.DELETE, "/api/v1/sites/" + siteId, null);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorCode(blocked)).isEqualTo("SITE_HAS_GROUPS");

    ResponseEntity<String> moved =
        call(
            sessionCookie,
            HttpMethod.DELETE,
            "/api/v1/sites/" + siteId + "?reassignTo=" + other,
            null);
    assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(groupOf(groupId).get("siteId").asText()).isEqualTo(other);
    assertThat(siteOfPlayer(playerId)).isEqualTo(other);

    ResponseEntity<String> cleared =
        call(sessionCookie, HttpMethod.DELETE, "/api/v1/sites/" + other + "?reassignTo=none", null);
    assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(groupOf(groupId).get("siteId").isNull()).isTrue();
    assertThat(siteOfPlayer(playerId)).isNull();
  }

  @Test
  void aSiteGrantReachesPlayersThatJoinThroughAGroup() throws Exception {
    String siteId = site("Europe/Madrid");
    String groupId = group(siteId);
    String playerId = playerIn(groupId);
    String viewer =
        userCookie(List.of("PLAYER_VIEW"), List.of(Map.of("siteId", siteId, "level", "VIEW")));

    ResponseEntity<String> response =
        call(viewer, HttpMethod.GET, "/api/v1/players/" + playerId, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
