package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.core.audit.service.AuditRetentionScheduler;
import java.net.URI;
import java.time.Instant;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/** Pruebas de la auditoría de la actividad de los usuarios. */
class AuditFlowTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "password-1234";

  @Autowired AuditRetentionScheduler retention;
  @Autowired JdbcTemplate jdbc;

  private final StandardWebSocketClient client = new StandardWebSocketClient();

  @BeforeEach
  void authenticate() {
    login();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private JsonNode audit(String query) throws Exception {
    return adminGet("/api/v1/audit?size=200&" + query).get("content");
  }

  private JsonNode first(String query) throws Exception {
    JsonNode content = audit(query);
    assertThat(content.size()).as("entradas para " + query).isGreaterThanOrEqualTo(1);
    return content.get(0);
  }

  private List<String> actions(JsonNode entries) {
    return entries.findValuesAsText("action");
  }

  private ResponseEntity<String> admin(HttpMethod method, String path, Object body) {
    return call(sessionCookie, method, path, body);
  }

  private String playlist(String name) throws Exception {
    return adminPost("/api/v1/playlists", Map.of("name", name, "items", List.of()))
        .get("id")
        .asText();
  }

  @Test
  void failedAndSuccessfulLoginsAreRecorded() throws Exception {
    String intruder = unique("intruso");
    ResponseEntity<String> rejected =
        rest.postForEntity(
            url("/api/v1/auth/login"),
            Map.of("username", intruder, "password", "wrong-password-1"),
            String.class);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    JsonNode denied = first("action=AUTH_LOGIN&outcome=DENIED&q=" + intruder);
    assertThat(denied.get("username").asText()).isEqualTo(intruder);
    assertThat(denied.get("userId").isNull()).isTrue();
    assertThat(denied.get("details").get("status").asInt()).isEqualTo(401);
    assertThat(denied.toString()).doesNotContain("wrong-password-1");

    String adminId = adminGet("/api/v1/auth/me").get("id").asText();
    JsonNode ok = first("action=AUTH_LOGIN&outcome=SUCCESS&userId=" + adminId);
    assertThat(ok.get("username").asText()).isEqualTo(ADMIN_USER);
    assertThat(ok.get("ip").asText()).isNotBlank();
  }

  @Test
  void playlistChangesAreRecordedWithBeforeAndAfter() throws Exception {
    String name = unique("lista");
    String id = playlist(name);
    String renamed = name + "-v2";
    admin(HttpMethod.PUT, "/api/v1/playlists/" + id, Map.of("name", renamed, "items", List.of()));
    admin(HttpMethod.DELETE, "/api/v1/playlists/" + id, null);

    JsonNode entries = audit("entityType=PLAYLIST&entityId=" + id);
    assertThat(actions(entries))
        .containsExactlyInAnyOrder("PLAYLIST_CREATE", "PLAYLIST_UPDATE", "PLAYLIST_DELETE");

    JsonNode created = first("action=PLAYLIST_CREATE&entityId=" + id);
    assertThat(created.get("entityName").asText()).isEqualTo(name);
    assertThat(created.get("details").get("state").get("name").asText()).isEqualTo(name);

    JsonNode updated = first("action=PLAYLIST_UPDATE&entityId=" + id);
    JsonNode change = updated.get("details").get("changes").get(0);
    assertThat(change.get("field").asText()).isEqualTo("name");
    assertThat(change.get("before").asText()).isEqualTo(name);
    assertThat(change.get("after").asText()).isEqualTo(renamed);
    assertThat(updated.get("entityName").asText()).isEqualTo(renamed);

    JsonNode deleted = first("action=PLAYLIST_DELETE&entityId=" + id);
    assertThat(deleted.get("entityName").asText()).isEqualTo(renamed);
    assertThat(deleted.get("details").get("state").get("name").asText()).isEqualTo(renamed);
  }

  @Test
  void secretsNeverReachTheAudit() throws Exception {
    String username = unique("sec");
    String userId = createUser(username, List.of());
    admin(
        HttpMethod.POST,
        "/api/v1/users/" + userId + "/password",
        Map.of("password", "Reset-Secret-777"));

    HttpHeaders json = new HttpHeaders();
    json.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    JsonNode requested =
        mapper.readTree(
            rest.exchange(
                    url("/player-api/pairing/request"),
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("hostname", unique("host")), json),
                    String.class)
                .getBody());
    String code = requested.get("code").asText();
    Map<String, Object> claim = new HashMap<>();
    claim.put("code", code);
    claim.put("name", unique("pantalla"));
    assertThat(admin(HttpMethod.POST, "/api/v1/pairings/claim", claim).getStatusCode().value())
        .isEqualTo(201);

    String everything = audit("").toString();
    assertThat(everything)
        .doesNotContain(PASSWORD)
        .doesNotContain("Reset-Secret-777")
        .doesNotContain(code)
        .doesNotContain(requested.get("secret").asText());
    assertThat(actions(audit("q=" + username))).contains("USER_CREATE", "USER_PASSWORD_RESET");
    JsonNode claimed = first("action=PAIRING_CLAIM");
    assertThat(claimed.get("entityType").asText()).isEqualTo("PLAYER");
  }

  @Test
  void deniedActionsAreRecordedAndOnlyAuditViewersCanRead() throws Exception {
    String username = unique("prog");
    createUser(username, List.of(schedulerGroup()));
    String scheduler = loginAs(username, PASSWORD);

    ResponseEntity<String> forbidden =
        call(scheduler, HttpMethod.POST, "/api/v1/users", Map.of("username", "x"));
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    ResponseEntity<String> audit = call(scheduler, HttpMethod.GET, "/api/v1/audit", null);
    assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(audit)).isEqualTo("PERMISSION_DENIED");
    assertThat(call(scheduler, HttpMethod.GET, "/api/v1/audit/stats", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(call(scheduler, HttpMethod.GET, "/api/v1/audit/export.csv", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    JsonNode denied = first("outcome=DENIED&q=" + username);
    assertThat(denied.get("username").asText()).isEqualTo(username);
    assertThat(actions(audit("outcome=DENIED&q=" + username)))
        .contains("USER_CREATE", "ACCESS_DENIED");

    String auditor = userCookie(List.of("AUDIT_VIEW"), List.of());
    assertThat(call(auditor, HttpMethod.GET, "/api/v1/audit", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    ResponseEntity<String> change =
        call(auditor, HttpMethod.PUT, "/api/v1/settings/audit", Map.of("retentionDays", 90));
    assertThat(change.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(change)).isEqualTo("ADMIN_REQUIRED");
  }

  @Test
  void filtersStatsActionsAndCsvWork() throws Exception {
    String formula = "=HYPERLINK(1)-" + unique("f");
    String id = playlist(formula);

    assertThat(audit("entityType=PLAYLIST&entityId=" + id).size()).isEqualTo(1);
    assertThat(audit("q=" + formula.toLowerCase()).size()).isGreaterThanOrEqualTo(1);
    assertThat(audit("entityId=" + id + "&outcome=FAILED").size()).isZero();
    assertThat(audit("entityId=" + id + "&from=" + Instant.now().plusSeconds(86400)).size())
        .isZero();
    assertThat(audit("entityId=" + id + "&to=" + Instant.now().minusSeconds(86400)).size())
        .isZero();
    assertThat(adminGet("/api/v1/audit?size=1&page=0").get("totalElements").asLong())
        .isGreaterThan(1);

    JsonNode stats = adminGet("/api/v1/audit/stats");
    assertThat(stats.get("total").asLong()).isGreaterThan(0);
    assertThat(stats.get("topActions").size()).isGreaterThan(0);
    assertThat(stats.get("topUsers").size()).isGreaterThan(0);
    assertThat(stats.get("byDay").size()).isGreaterThan(0);
    assertThat(stats.get("byOutcome").has("SUCCESS")).isTrue();

    assertThat(actions(adminGet("/api/v1/audit/actions")))
        .contains("PLAYLIST_CREATE", "PLAYER_REMOTE_START", "AUTH_LOGIN_MICROSOFT");

    ResponseEntity<String> csv =
        rest.exchange(
            url("/api/v1/audit/export.csv?entityId=" + id),
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders()),
            String.class);
    assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(csv.getHeaders().getContentType().toString()).startsWith("text/csv");
    assertThat(csv.getBody())
        .contains("at,username,action,entityType,entityId,entityName,outcome,ip,details")
        .contains("PLAYLIST_CREATE")
        .contains("'" + formula)
        .doesNotContain("\n" + formula);
    assertThat(first("action=AUDIT_EXPORT").get("username").asText()).isEqualTo(ADMIN_USER);
  }

  @Test
  void retentionIsConfigurableAndThePurgeAuditsItself() throws Exception {
    assertThat(
            admin(HttpMethod.PUT, "/api/v1/settings/audit", Map.of("retentionDays", 10))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode updated =
        mapper.readTree(
            admin(HttpMethod.PUT, "/api/v1/settings/audit", Map.of("retentionDays", 60)).getBody());
    assertThat(updated.get("retentionDays").asInt()).isEqualTo(60);
    assertThat(first("action=AUDIT_SETTINGS_UPDATE").get("entityName").asText()).isEqualTo("audit");

    jdbc.update(
        "insert into audit_logs (id, at, action, outcome) values (?, now() - interval '90 days',"
            + " 'OLD_ACTION', 'SUCCESS')",
        UUID.randomUUID());
    assertThat(audit("action=OLD_ACTION").size()).isEqualTo(1);

    assertThat(retention.purge()).isGreaterThanOrEqualTo(1);

    assertThat(audit("action=OLD_ACTION").size()).isZero();
    JsonNode purge = first("action=AUDIT_PURGE");
    assertThat(purge.get("username").asText()).isEqualTo("system");
    assertThat(purge.get("details").get("retentionDays").asInt()).isEqualTo(60);
    admin(HttpMethod.PUT, "/api/v1/settings/audit", Map.of("retentionDays", 365));
  }

  @Test
  void remoteAndTerminalSessionsAreAudited() throws Exception {
    JsonNode enrolled = enroll(unique("pantalla-audit"));
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();
    SocketCollector playerSide = new SocketCollector();
    WebSocketSession player =
        client
            .execute(
                playerSide,
                new WebSocketHttpHeaders(),
                URI.create(wsUrl("/ws/player?token=" + token)))
            .get(10, TimeUnit.SECONDS);
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.COOKIE, sessionCookie);

    SocketCollector remoteSide = new SocketCollector();
    WebSocketSession remote =
        client
            .execute(remoteSide, headers, URI.create(wsUrl("/ws/remote/" + playerId)))
            .get(10, TimeUnit.SECONDS);
    remote.sendMessage(new TextMessage("{\"type\":\"START\"}"));
    playerSide.nextContaining("REMOTE_START");
    remote.sendMessage(new TextMessage("{\"type\":\"INPUT\",\"kind\":\"TEXT\",\"text\":\"hola\"}"));
    playerSide.nextContaining("hola");
    remote.sendMessage(new TextMessage("{\"type\":\"STOP\"}"));
    playerSide.nextContaining("REMOTE_STOP");
    remote.close();

    SocketCollector shellSide = new SocketCollector();
    WebSocketSession shell =
        client
            .execute(shellSide, headers, URI.create(wsUrl("/ws/shell/" + playerId)))
            .get(10, TimeUnit.SECONDS);
    shell.sendMessage(new TextMessage("{\"type\":\"OPEN\",\"cols\":80,\"rows\":24}"));
    playerSide.nextContaining("SHELL_OPEN");
    shell.sendMessage(new TextMessage("{\"type\":\"CLOSE\"}"));
    playerSide.nextContaining("SHELL_CLOSE");
    shell.close();
    player.close();

    waitFor("action=PLAYER_REMOTE_STOP&entityId=" + playerId);
    waitFor("action=PLAYER_SHELL_CLOSE&entityId=" + playerId);
    assertThat(actions(audit("entityId=" + playerId)))
        .contains(
            "PLAYER_REMOTE_START", "PLAYER_REMOTE_STOP", "PLAYER_SHELL_OPEN", "PLAYER_SHELL_CLOSE");
    JsonNode stop = first("action=PLAYER_REMOTE_STOP&entityId=" + playerId);
    assertThat(stop.get("details").get("inputs").asInt()).isEqualTo(1);
    assertThat(stop.get("username").asText()).isEqualTo(ADMIN_USER);
    assertThat(audit("entityId=" + playerId).toString()).doesNotContain("hola");
  }

  @Test
  void aDeniedRemoteHandshakeIsRecorded() throws Exception {
    String playerId = enroll(unique("pantalla-denegada")).get("playerId").asText();
    String username = unique("sinacceso");
    createUser(username, List.of(schedulerGroup()));
    String cookie = loginAs(username, PASSWORD);
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.COOKIE, cookie);

    assertThatThrownBy(
            () ->
                client
                    .execute(
                        new SocketCollector(), headers, URI.create(wsUrl("/ws/remote/" + playerId)))
                    .get(10, TimeUnit.SECONDS))
        .isInstanceOf(java.util.concurrent.ExecutionException.class);

    JsonNode denied = first("action=PLAYER_REMOTE_START&outcome=DENIED&q=" + username);
    assertThat(denied.get("entityId").asText()).isEqualTo(playerId);
  }

  private void waitFor(String query) throws Exception {
    for (int i = 0; i < 50 && audit(query).isEmpty(); i++) {
      Thread.sleep(100);
    }
  }
}
