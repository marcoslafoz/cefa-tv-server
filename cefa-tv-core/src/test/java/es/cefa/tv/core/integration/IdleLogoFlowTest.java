package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/** Pruebas del logo de la pantalla sin programación: herencia, entrega y permisos. */
class IdleLogoFlowTest extends AbstractIntegrationTest {

  private final StandardWebSocketClient client = new StandardWebSocketClient();

  @BeforeEach
  void authenticate() {
    login();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String group(Map<String, Object> extra) throws Exception {
    Map<String, Object> body = new HashMap<>(extra);
    body.put("name", unique("Grupo"));
    ResponseEntity<String> response = call(sessionCookie, HttpMethod.POST, "/api/v1/groups", body);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
    return mapper.readTree(response.getBody()).get("id").asText();
  }

  private ResponseEntity<String> updateGroup(
      String cookie, String id, String name, Map<String, Object> extra) {
    Map<String, Object> body = new HashMap<>(extra);
    body.put("name", name);
    return call(cookie, HttpMethod.PUT, "/api/v1/groups/" + id, body);
  }

  private String groupName(String id) throws Exception {
    return groupJson(id).get("name").asText();
  }

  private JsonNode groupJson(String id) throws Exception {
    for (JsonNode group : adminGet("/api/v1/groups")) {
      if (group.get("id").asText().equals(id)) {
        return group;
      }
    }
    throw new AssertionError("Grupo no encontrado " + id);
  }

  private ResponseEntity<String> patchPlayer(String cookie, String id, Map<String, ?> body) {
    return call(cookie, HttpMethod.PATCH, "/api/v1/players/" + id, body);
  }

  private JsonNode player(String id) throws Exception {
    return adminGet("/api/v1/players/" + id);
  }

  private void assertEffective(String id, String logo, String source) throws Exception {
    JsonNode player = player(id);
    assertThat(player.get("effectiveIdleLogo").asText()).isEqualTo(logo);
    assertThat(player.get("idleLogoSource").asText()).isEqualTo(source);
  }

  private JsonNode heartbeatConfig(String token) throws Exception {
    Map<String, Object> heartbeat = new HashMap<>();
    heartbeat.put("appliedVersion", 0);
    heartbeat.put("state", "IDLE");
    return playerPost(token, "/player-api/heartbeat", heartbeat).get("config");
  }

  private SocketCollector connect(String token) throws Exception {
    SocketCollector side = new SocketCollector();
    WebSocketSession session =
        client
            .execute(
                side, new WebSocketHttpHeaders(), URI.create(wsUrl("/ws/player?token=" + token)))
            .get(10, TimeUnit.SECONDS);
    side.session = session;
    session.sendMessage(new TextMessage("{\"type\":\"HELLO\",\"appliedVersion\":0}"));
    return side;
  }

  @Test
  void aNewPlayerShowsTheDefaultLogoAndOwnsNone() throws Exception {
    JsonNode enrolled = enroll(unique("pantalla"));
    String id = enrolled.get("playerId").asText();

    JsonNode player = player(id);
    assertThat(player.get("idleLogo").isNull()).isTrue();
    assertEffective(id, "CEFA", "DEFAULT");
    assertThat(heartbeatConfig(enrolled.get("deviceToken").asText()).get("idleLogo").asText())
        .isEqualTo("CEFA");
  }

  @Test
  void aPlayerChoosesItsOwnLogoAndCanGoBackToInheriting() throws Exception {
    JsonNode enrolled = enroll(unique("pantalla"));
    String id = enrolled.get("playerId").asText();
    String token = enrolled.get("deviceToken").asText();

    ResponseEntity<String> response =
        patchPlayer(sessionCookie, id, Map.of("idleLogo", "ALGONTEC"));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode updated = mapper.readTree(response.getBody());
    assertThat(updated.get("idleLogo").asText()).isEqualTo("ALGONTEC");
    assertThat(updated.get("effectiveIdleLogo").asText()).isEqualTo("ALGONTEC");
    assertThat(updated.get("idleLogoSource").asText()).isEqualTo("PLAYER");
    assertThat(heartbeatConfig(token).get("idleLogo").asText()).isEqualTo("ALGONTEC");

    patchPlayer(sessionCookie, id, Map.of("clearIdleLogo", true));
    assertThat(player(id).get("idleLogo").isNull()).isTrue();
    assertEffective(id, "CEFA", "DEFAULT");
    assertThat(heartbeatConfig(token).get("idleLogo").asText()).isEqualTo("CEFA");
  }

  @Test
  void anUnknownLogoIsRejected() throws Exception {
    String id = enroll(unique("pantalla")).get("playerId").asText();

    ResponseEntity<String> response = patchPlayer(sessionCookie, id, Map.of("idleLogo", "OTRO"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void theGroupLogoIsInheritedUnlessThePlayerChoosesItsOwn() throws Exception {
    String groupId = group(Map.of());
    String inheriting = enroll(unique("hereda"), groupId).get("playerId").asText();
    String own = enroll(unique("propio"), groupId).get("playerId").asText();
    String outsider = enroll(unique("fuera")).get("playerId").asText();
    patchPlayer(sessionCookie, own, Map.of("idleLogo", "CEFA"));

    ResponseEntity<String> response =
        updateGroup(sessionCookie, groupId, groupName(groupId), Map.of("idleLogo", "ALGONTEC"));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(mapper.readTree(response.getBody()).get("idleLogo").asText()).isEqualTo("ALGONTEC");
    assertEffective(inheriting, "ALGONTEC", "GROUP");
    assertEffective(own, "CEFA", "PLAYER");
    assertEffective(outsider, "CEFA", "DEFAULT");
    assertThat(groupJson(groupId).get("idleLogo").asText()).isEqualTo("ALGONTEC");

    JsonNode audit =
        adminGet("/api/v1/audit?size=20&action=GROUP_IDLE_LOGO_CHANGE&entityId=" + groupId)
            .get("content");
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0).get("details").get("affectedPlayers").asInt()).isEqualTo(1);

    updateGroup(sessionCookie, groupId, groupName(groupId), Map.of("clearIdleLogo", true));
    assertEffective(inheriting, "CEFA", "DEFAULT");
    assertThat(groupJson(groupId).get("idleLogo").isNull()).isTrue();
  }

  @Test
  void movingBetweenGroupsChangesTheEffectiveLogoAndDeletingTheGroupDropsIt() throws Exception {
    String algontec = group(Map.of("idleLogo", "ALGONTEC"));
    String plain = group(Map.of());
    String id = enroll(unique("pantalla"), plain).get("playerId").asText();
    assertEffective(id, "CEFA", "DEFAULT");

    patchPlayer(sessionCookie, id, Map.of("groupId", algontec));
    assertEffective(id, "ALGONTEC", "GROUP");

    patchPlayer(sessionCookie, id, Map.of("clearGroup", true));
    assertEffective(id, "CEFA", "DEFAULT");

    patchPlayer(sessionCookie, id, Map.of("groupId", algontec));
    ResponseEntity<String> deleted =
        call(sessionCookie, HttpMethod.DELETE, "/api/v1/groups/" + algontec, null);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertEffective(id, "CEFA", "DEFAULT");
  }

  @Test
  void theEffectiveLogoReachesTheConnectedPlayerImmediatelyAndOnEveryHello() throws Exception {
    String groupId = group(Map.of());
    JsonNode enrolled = enroll(unique("pantalla"), groupId);
    String id = enrolled.get("playerId").asText();
    String token = enrolled.get("deviceToken").asText();
    SocketCollector socket = connect(token);
    assertThat(mapper.readTree(socket.nextContaining("CONFIG")).get("idleLogo").asText())
        .isEqualTo("CEFA");

    updateGroup(sessionCookie, groupId, groupName(groupId), Map.of("idleLogo", "ALGONTEC"));
    assertThat(mapper.readTree(socket.nextContaining("CONFIG")).get("idleLogo").asText())
        .isEqualTo("ALGONTEC");

    patchPlayer(sessionCookie, id, Map.of("idleLogo", "CEFA"));
    assertThat(mapper.readTree(socket.nextContaining("CONFIG")).get("idleLogo").asText())
        .isEqualTo("CEFA");
    socket.session.close();

    SocketCollector again = connect(token);
    JsonNode hello = mapper.readTree(again.nextContaining("CONFIG"));
    assertThat(hello.get("idleLogo").asText()).isEqualTo("CEFA");
    assertThat(hello.has("preferredOutput")).isTrue();
    again.session.close();
  }

  @Test
  void aPlayersGroupChangeIsPushedToItsConnection() throws Exception {
    String algontec = group(Map.of("idleLogo", "ALGONTEC"));
    JsonNode enrolled = enroll(unique("pantalla"));
    String id = enrolled.get("playerId").asText();
    SocketCollector socket = connect(enrolled.get("deviceToken").asText());
    socket.nextContaining("CONFIG");

    patchPlayer(sessionCookie, id, Map.of("groupId", algontec));

    assertThat(mapper.readTree(socket.nextContaining("CONFIG")).get("idleLogo").asText())
        .isEqualTo("ALGONTEC");
    socket.session.close();
  }

  @Test
  void auditRecordsThePlayerLogoChange() throws Exception {
    String id = enroll(unique("pantalla")).get("playerId").asText();

    patchPlayer(sessionCookie, id, Map.of("idleLogo", "ALGONTEC"));

    JsonNode audit =
        adminGet("/api/v1/audit?size=20&action=PLAYER_IDLE_LOGO_CHANGE&entityId=" + id)
            .get("content");
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0).get("details").get("changes").get(0).get("after").asText())
        .isEqualTo("ALGONTEC");
  }

  @Test
  void permissionsFollowThePlayerAndGroupRules() throws Exception {
    String groupId = group(Map.of());
    String id = enroll(unique("pantalla"), groupId).get("playerId").asText();
    String name = groupName(groupId);
    List<Map<String, Object>> operate =
        List.of(Map.of("playerGroupId", groupId, "level", "OPERATE"));
    String manager = userCookie(List.of("PLAYER_MANAGE", "PLAYER_VIEW"), operate);
    String scheduler = userCookie(List.of("SCHEDULE_MANAGE", "PLAYER_VIEW"), operate);
    String viewer =
        userCookie(
            List.of("PLAYER_MANAGE", "PLAYER_VIEW"),
            List.of(Map.of("playerGroupId", groupId, "level", "VIEW")));

    assertThat(patchPlayer(manager, id, Map.of("idleLogo", "ALGONTEC")).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(patchPlayer(scheduler, id, Map.of("idleLogo", "CEFA")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(patchPlayer(viewer, id, Map.of("idleLogo", "CEFA")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(player(id).get("idleLogo").asText()).isEqualTo("ALGONTEC");

    ResponseEntity<String> denied =
        updateGroup(manager, groupId, name, Map.of("idleLogo", "ALGONTEC"));
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(denied)).isEqualTo("ADMIN_REQUIRED");
    assertThat(groupJson(groupId).get("idleLogo").isNull()).isTrue();
  }
}
