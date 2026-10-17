package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.core.usergroup.model.UserGroup;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/** Permisos y accesos como unión de los de los grupos de usuarios de cada usuario. */
class AccessControlTest extends AbstractIntegrationTest {

  private static final String ADMINISTRATORS = UserGroup.ADMINISTRATORS_ID.toString();

  private final StandardWebSocketClient client = new StandardWebSocketClient();

  @BeforeEach
  void authenticate() {
    login();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String group() throws Exception {
    return adminPost("/api/v1/groups", Map.of("name", unique("grupo"))).get("id").asText();
  }

  private String player(String groupId) throws Exception {
    return enroll(unique("pantalla"), groupId).get("playerId").asText();
  }

  private Map<String, Object> grant(String groupId, String level) {
    return Map.of("playerGroupId", groupId, "level", level);
  }

  private Map<String, Object> scheduleFor(
      String assetId, List<String> players, List<String> groups) {
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
        Map.of("playerIds", players, "groupIds", groups));
  }

  private List<String> ids(String body) throws Exception {
    return mapper.readTree(body).findValuesAsText("id");
  }

  private JsonNode me(String cookie) throws Exception {
    return mapper.readTree(call(cookie, HttpMethod.GET, "/api/v1/auth/me", null).getBody());
  }

  private JsonNode adminPut(String path, Object body) throws Exception {
    ResponseEntity<String> response = call(sessionCookie, HttpMethod.PUT, path, body);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    return mapper.readTree(response.getBody());
  }

  private WebSocketSession connect(String path, String cookie) throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    if (cookie != null) {
      headers.add(HttpHeaders.COOKIE, cookie);
    }
    return client
        .execute(new SocketCollector(), headers, URI.create(wsUrl(path)))
        .get(10, TimeUnit.SECONDS);
  }

  @Test
  void aSchedulerOnlySeesAndSchedulesTheGroupsItWasGranted() throws Exception {
    String groupA = group();
    String groupB = group();
    String playerA = player(groupA);
    String playerB = player(groupB);
    String playerFree = player(null);
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();
    String scheduler = userCookie(SCHEDULER_PERMISSIONS, List.of(grant(groupA, "OPERATE")));

    List<String> visible = ids(call(scheduler, HttpMethod.GET, "/api/v1/players", null).getBody());
    assertThat(visible).contains(playerA).doesNotContain(playerB, playerFree);
    assertThat(call(scheduler, HttpMethod.GET, "/api/v1/players/" + playerB, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    ResponseEntity<String> free =
        call(scheduler, HttpMethod.GET, "/api/v1/players/" + playerFree, null);
    assertThat(errorCode(free)).isEqualTo("SCOPE_DENIED");
    List<String> groups = ids(call(scheduler, HttpMethod.GET, "/api/v1/groups", null).getBody());
    assertThat(groups).contains(groupA).doesNotContain(groupB);
    JsonNode dashboard =
        mapper.readTree(call(scheduler, HttpMethod.GET, "/api/v1/dashboard", null).getBody());
    assertThat(dashboard.get("players").asInt()).isEqualTo(1);

    ResponseEntity<String> created =
        call(
            scheduler,
            HttpMethod.POST,
            "/api/v1/schedules",
            scheduleFor(assetId, List.of(playerA), List.of()));
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(
            errorCode(
                call(
                    scheduler,
                    HttpMethod.POST,
                    "/api/v1/schedules",
                    scheduleFor(assetId, List.of(playerB), List.of()))))
        .isEqualTo("SCOPE_DENIED");
    assertThat(
            call(
                    scheduler,
                    HttpMethod.POST,
                    "/api/v1/schedules",
                    scheduleFor(assetId, List.of(), List.of(groupB)))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    scheduler,
                    HttpMethod.POST,
                    "/api/v1/schedules",
                    scheduleFor(assetId, List.of(playerFree), List.of()))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    JsonNode foreign =
        adminPost("/api/v1/schedules", scheduleFor(assetId, List.of(playerB), List.of()));
    String foreignId = foreign.get("id").asText();
    List<String> schedules =
        ids(call(scheduler, HttpMethod.GET, "/api/v1/schedules", null).getBody());
    assertThat(schedules).doesNotContain(foreignId);
    assertThat(
            call(scheduler, HttpMethod.GET, "/api/v1/schedules/" + foreignId, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    scheduler,
                    HttpMethod.PUT,
                    "/api/v1/schedules/" + foreignId,
                    scheduleFor(assetId, List.of(playerA), List.of()))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(scheduler, HttpMethod.DELETE, "/api/v1/schedules/" + foreignId, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    scheduler,
                    HttpMethod.GET,
                    "/api/v1/schedules/timeline?playerId="
                        + playerB
                        + "&from=2026-10-01&to=2026-10-02",
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void aUserWithoutGroupsHasNoPermissionsAndSeesNothing() throws Exception {
    String groupA = group();
    String playerA = player(groupA);
    String username = unique("sin-grupos");
    createUser(username, List.of());
    String cookie = loginAs(username, USER_PASSWORD);

    JsonNode profile = me(cookie);
    assertThat(profile.get("groups")).isEmpty();
    assertThat(profile.get("permissions")).isEmpty();
    assertThat(profile.get("grants")).isEmpty();
    assertThat(profile.get("admin").asBoolean()).isFalse();
    ResponseEntity<String> players = call(cookie, HttpMethod.GET, "/api/v1/players", null);
    assertThat(players.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(players)).isEqualTo("PERMISSION_DENIED");
    assertThat(call(cookie, HttpMethod.GET, "/api/v1/players/" + playerA, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(call(cookie, HttpMethod.GET, "/api/v1/schedules", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    cookie,
                    HttpMethod.POST,
                    "/api/v1/playlists",
                    Map.of("name", "x", "items", List.of()))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void aSchedulerLacksThePlayerAndUserManagementPermissions() throws Exception {
    String groupA = group();
    String playerA = player(groupA);
    String scheduler = userCookie(SCHEDULER_PERMISSIONS, List.of(grant(groupA, "OPERATE")));

    ResponseEntity<String> restart =
        call(scheduler, HttpMethod.POST, "/api/v1/players/" + playerA + "/restart", null);
    assertThat(restart.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(restart)).isEqualTo("PERMISSION_DENIED");
    assertThat(call(scheduler, HttpMethod.GET, "/api/v1/users", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(call(scheduler, HttpMethod.GET, "/api/v1/user-groups", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(call(scheduler, HttpMethod.GET, "/api/v1/roles", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(scheduler, HttpMethod.GET, "/api/v1/pairings/pending", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(scheduler, HttpMethod.GET, "/api/v1/players/" + playerA + "/shell-audits", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(scheduler, HttpMethod.POST, "/api/v1/groups", Map.of("name", unique("nuevo")))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void aViewerCanOnlyRead() throws Exception {
    String groupA = group();
    String playerA = player(groupA);
    String viewer = userCookie(VIEWER_PERMISSIONS, List.of(grant(groupA, "VIEW")));

    assertThat(ids(call(viewer, HttpMethod.GET, "/api/v1/players", null).getBody()))
        .contains(playerA);
    assertThat(
            call(viewer, HttpMethod.GET, "/api/v1/players/" + playerA + "/metrics?hours=1", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(call(viewer, HttpMethod.GET, "/api/v1/media", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            errorCode(
                call(
                    viewer,
                    HttpMethod.POST,
                    "/api/v1/media/web",
                    Map.of("name", "x", "url", "https://example.com"))))
        .isEqualTo("PERMISSION_DENIED");
    assertThat(
            call(
                    viewer,
                    HttpMethod.PATCH,
                    "/api/v1/players/" + playerA,
                    Map.of("name", "Renombrado"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    viewer,
                    HttpMethod.POST,
                    "/api/v1/playlists",
                    Map.of("name", "p", "items", List.of()))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void aViewGrantIsNotEnoughToOperate() throws Exception {
    String groupA = group();
    String playerA = player(groupA);
    String assetId = uploadImage(unique("img") + ".png").get("id").asText();
    String scheduler = userCookie(SCHEDULER_PERMISSIONS, List.of(grant(groupA, "VIEW")));

    assertThat(ids(call(scheduler, HttpMethod.GET, "/api/v1/players", null).getBody()))
        .contains(playerA);
    ResponseEntity<String> denied =
        call(
            scheduler,
            HttpMethod.POST,
            "/api/v1/schedules",
            scheduleFor(assetId, List.of(playerA), List.of()));
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(denied)).isEqualTo("SCOPE_DENIED");
    assertThat(
            call(
                    scheduler,
                    HttpMethod.PATCH,
                    "/api/v1/players/" + playerA,
                    Map.of("orientation", "vertical"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void effectivePermissionsAndGrantsAreTheUnionOfAllTheUserGroups() throws Exception {
    String groupA = group();
    String groupB = group();
    String viewers =
        createGroup(
            unique("equipo-a"),
            List.of("PLAYER_VIEW", "SCHEDULE_MANAGE"),
            List.of(grant(groupA, "VIEW"), grant(groupB, "VIEW")));
    String operators =
        createGroup(unique("equipo-b"), List.of("MEDIA_MANAGE"), List.of(grant(groupA, "OPERATE")));
    String username = unique("mixto");
    createUser(username, List.of(viewers, operators));
    String cookie = loginAs(username, USER_PASSWORD);

    JsonNode profile = me(cookie);

    Map<String, String> levels = new HashMap<>();
    profile
        .get("grants")
        .forEach(g -> levels.put(g.get("playerGroupId").asText(), g.get("level").asText()));
    assertThat(levels).containsEntry(groupA, "OPERATE").containsEntry(groupB, "VIEW");
    assertThat(profile.get("admin").asBoolean()).isFalse();
    assertThat(profile.get("groups")).hasSize(2);
    assertThat(profile.get("permissions").toString())
        .contains("PLAYER_VIEW", "SCHEDULE_MANAGE", "MEDIA_MANAGE")
        .doesNotContain("USER_MANAGE");
    assertThat(profile.has("role")).isFalse();
  }

  @Test
  void anAllAccessGroupMakesItsMembersAdministratorsOfEverything() throws Exception {
    String playerFree = player(null);
    String allAccess =
        mapper
            .readTree(
                call(
                        sessionCookie,
                        HttpMethod.POST,
                        "/api/v1/user-groups",
                        Map.of("name", unique("todo"), "allAccess", true))
                    .getBody())
            .get("id")
            .asText();
    String username = unique("jefe");
    createUser(username, List.of(allAccess));
    String cookie = loginAs(username, USER_PASSWORD);

    JsonNode profile = me(cookie);
    assertThat(profile.get("admin").asBoolean()).isTrue();
    assertThat(profile.get("grants")).isEmpty();
    assertThat(profile.get("permissions")).hasSize(9);
    assertThat(ids(call(cookie, HttpMethod.GET, "/api/v1/players", null).getBody()))
        .contains(playerFree);
    assertThat(
            call(cookie, HttpMethod.POST, "/api/v1/sites", Map.of("name", unique("sede")))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    JsonNode group =
        mapper.readTree(
            call(cookie, HttpMethod.GET, "/api/v1/user-groups/" + allAccess, null).getBody());
    assertThat(group.get("allAccess").asBoolean()).isTrue();
    assertThat(group.get("permissions")).hasSize(9);
  }

  @Test
  void aDisabledUserLosesAccessImmediately() throws Exception {
    String username = unique("temporal");
    String userId =
        createUser(username, List.of(createGroup(unique("g"), VIEWER_PERMISSIONS, List.of())));
    String cookie = loginAs(username, USER_PASSWORD);
    assertThat(call(cookie, HttpMethod.GET, "/api/v1/auth/me", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<String> update =
        call(
            sessionCookie,
            HttpMethod.PUT,
            "/api/v1/users/" + userId,
            Map.of("displayName", "Temporal", "enabled", false));
    assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(call(cookie, HttpMethod.GET, "/api/v1/auth/me", null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(loginAs(username, USER_PASSWORD)).isNull();
  }

  @Test
  void editingAGroupOrItsMembersTakesEffectAtOnce() throws Exception {
    String groupId = createGroup(unique("cambiante"), List.of("PLAYER_VIEW"), List.of());
    String username = unique("miembro");
    String userId = createUser(username, List.of(groupId));
    String cookie = loginAs(username, USER_PASSWORD);
    assertThat(me(cookie).get("permissions").toString()).contains("PLAYER_VIEW");
    assertThat(call(cookie, HttpMethod.GET, "/api/v1/players", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    adminPut(
        "/api/v1/user-groups/" + groupId,
        Map.of("name", unique("cambiante"), "permissions", List.of("MEDIA_MANAGE")));
    JsonNode afterEdit = me(cookie);
    assertThat(afterEdit.get("permissions").toString())
        .contains("MEDIA_MANAGE")
        .doesNotContain("PLAYER_VIEW");
    assertThat(call(cookie, HttpMethod.GET, "/api/v1/players", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    call(
        sessionCookie,
        HttpMethod.PUT,
        "/api/v1/users/" + userId,
        Map.of("displayName", username, "enabled", true, "groupIds", List.of()));
    assertThat(me(cookie).get("groups")).isEmpty();
    assertThat(me(cookie).get("permissions")).isEmpty();
  }

  @Test
  void theLastAdministratorCannotBeRemovedDisabledOrTakenOutOfTheAdminGroup() throws Exception {
    String adminId = adminGet("/api/v1/auth/me").get("id").asText();
    for (JsonNode other : adminGet("/api/v1/users")) {
      if (other.get("admin").asBoolean() && !adminId.equals(other.get("id").asText())) {
        call(sessionCookie, HttpMethod.DELETE, "/api/v1/users/" + other.get("id").asText(), null);
      }
    }

    ResponseEntity<String> disable =
        call(
            sessionCookie,
            HttpMethod.PUT,
            "/api/v1/users/" + adminId,
            Map.of("displayName", "admin", "enabled", false));
    assertThat(errorCode(disable)).isEqualTo("LAST_ADMIN");
    ResponseEntity<String> leaveGroup =
        call(
            sessionCookie,
            HttpMethod.PUT,
            "/api/v1/users/" + adminId,
            Map.of("displayName", "admin", "enabled", true, "groupIds", List.of()));
    assertThat(errorCode(leaveGroup)).isEqualTo("LAST_ADMIN");
    ResponseEntity<String> emptyGroup =
        call(
            sessionCookie,
            HttpMethod.PUT,
            "/api/v1/user-groups/" + ADMINISTRATORS,
            Map.of("name", "Administradores", "memberIds", List.of()));
    assertThat(errorCode(emptyGroup)).isEqualTo("LAST_ADMIN");
    ResponseEntity<String> deleteSelf =
        call(sessionCookie, HttpMethod.DELETE, "/api/v1/users/" + adminId, null);
    assertThat(errorCode(deleteSelf)).isEqualTo("CANNOT_DELETE_SELF");
    assertThat(adminGet("/api/v1/auth/me").get("admin").asBoolean()).isTrue();

    String second = createUser(unique("segundo-admin"), List.of(ADMINISTRATORS));
    assertThat(
            call(sessionCookie, HttpMethod.DELETE, "/api/v1/users/" + second, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void theBuiltinAdministratorsGroupIsImmutableButItsMembersCanChange() throws Exception {
    JsonNode administrators = adminGet("/api/v1/user-groups/" + ADMINISTRATORS);
    assertThat(administrators.get("builtin").asBoolean()).isTrue();
    assertThat(administrators.get("allAccess").asBoolean()).isTrue();
    assertThat(administrators.get("permissions")).hasSize(9);
    assertThat(administrators.get("name").asText()).isEqualTo("Administradores");

    assertThat(
            errorCode(
                call(
                    sessionCookie,
                    HttpMethod.PUT,
                    "/api/v1/user-groups/" + ADMINISTRATORS,
                    Map.of("name", "Jefes"))))
        .isEqualTo("USER_GROUP_BUILTIN");
    assertThat(
            errorCode(
                call(
                    sessionCookie,
                    HttpMethod.PUT,
                    "/api/v1/user-groups/" + ADMINISTRATORS,
                    Map.of("name", "Administradores", "allAccess", false))))
        .isEqualTo("USER_GROUP_BUILTIN");
    assertThat(
            errorCode(
                call(
                    sessionCookie,
                    HttpMethod.DELETE,
                    "/api/v1/user-groups/" + ADMINISTRATORS,
                    null)))
        .isEqualTo("USER_GROUP_BUILTIN");

    String adminId = adminGet("/api/v1/auth/me").get("id").asText();
    String other = createUser(unique("otro-admin"), List.of());
    JsonNode updated =
        adminPut(
            "/api/v1/user-groups/" + ADMINISTRATORS,
            Map.of("name", "Administradores", "memberIds", List.of(adminId, other)));
    assertThat(updated.get("memberCount").asInt()).isEqualTo(2);
    assertThat(updated.get("memberIds").toString()).contains(other);
  }

  @Test
  void customGroupsHaveAFullLifecycle() throws Exception {
    String name = unique("equipo");
    ResponseEntity<String> created =
        call(
            sessionCookie,
            HttpMethod.POST,
            "/api/v1/user-groups",
            Map.of(
                "name",
                name,
                "permissions",
                List.of("SCHEDULE_MANAGE", "PLAYER_VIEW", "AUDIT_VIEW")));
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode group = mapper.readTree(created.getBody());
    String id = group.get("id").asText();
    assertThat(group.get("builtin").asBoolean()).isFalse();
    assertThat(group.get("allAccess").asBoolean()).isFalse();
    assertThat(group.get("permissions").toString())
        .isEqualTo("[\"AUDIT_VIEW\",\"PLAYER_VIEW\",\"SCHEDULE_MANAGE\"]");

    assertThat(
            errorCode(
                call(
                    sessionCookie,
                    HttpMethod.POST,
                    "/api/v1/user-groups",
                    Map.of("name", unique("malo"), "permissions", List.of("VOLAR")))))
        .isEqualTo("UNKNOWN_PERMISSION");
    assertThat(
            errorCode(
                call(
                    sessionCookie,
                    HttpMethod.POST,
                    "/api/v1/user-groups",
                    Map.of("name", name.toUpperCase()))))
        .isEqualTo("USER_GROUP_NAME_EXISTS");

    String renamed = name + "-v2";
    JsonNode updated =
        adminPut(
            "/api/v1/user-groups/" + id,
            Map.of("name", renamed, "permissions", List.of("MEDIA_MANAGE")));
    assertThat(updated.get("name").asText()).isEqualTo(renamed);
    assertThat(updated.get("permissions").toString()).isEqualTo("[\"MEDIA_MANAGE\"]");
    assertThat(ids(call(sessionCookie, HttpMethod.GET, "/api/v1/user-groups", null).getBody()))
        .contains(id, ADMINISTRATORS);

    assertThat(
            call(sessionCookie, HttpMethod.DELETE, "/api/v1/user-groups/" + id, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            call(sessionCookie, HttpMethod.GET, "/api/v1/user-groups/" + id, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aUserManagerCannotEscalatePrivileges() throws Exception {
    String groupId = group();
    String managerCookie =
        userCookie(List.of("USER_MANAGE", "PLAYER_VIEW"), List.of(grant(groupId, "OPERATE")));

    ResponseEntity<String> allAccess =
        call(
            managerCookie,
            HttpMethod.POST,
            "/api/v1/user-groups",
            Map.of("name", unique("todo"), "allAccess", true));
    assertThat(allAccess.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(allAccess)).isEqualTo("ADMIN_REQUIRED");
    ResponseEntity<String> moreThanOwn =
        call(
            managerCookie,
            HttpMethod.POST,
            "/api/v1/user-groups",
            Map.of("name", unique("terminal"), "permissions", List.of("PLAYER_TERMINAL")));
    assertThat(errorCode(moreThanOwn)).isEqualTo("PRIVILEGE_ESCALATION");
    ResponseEntity<String> ownLevel =
        call(
            managerCookie,
            HttpMethod.POST,
            "/api/v1/user-groups",
            Map.of("name", unique("ver"), "permissions", List.of("PLAYER_VIEW")));
    assertThat(ownLevel.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    String terminalGroup = createGroup(unique("terminal"), List.of("PLAYER_TERMINAL"), List.of());
    String victim = createUser(unique("victima"), List.of());
    ResponseEntity<String> assign =
        call(
            managerCookie,
            HttpMethod.PUT,
            "/api/v1/users/" + victim,
            Map.of("displayName", "v", "enabled", true, "groupIds", List.of(terminalGroup)));
    assertThat(errorCode(assign)).isEqualTo("PRIVILEGE_ESCALATION");
    ResponseEntity<String> makeAdmin =
        call(
            managerCookie,
            HttpMethod.PUT,
            "/api/v1/users/" + victim,
            Map.of("displayName", "v", "enabled", true, "groupIds", List.of(ADMINISTRATORS)));
    assertThat(errorCode(makeAdmin)).isEqualTo("ADMIN_REQUIRED");
  }

  @Test
  void userGroupsMergeDuplicateGrantsAndDropThemWhenDeleted() throws Exception {
    String groupA = group();
    String name = unique("equipo");
    JsonNode created =
        adminPost(
            "/api/v1/user-groups",
            Map.of(
                "name", name, "grants", List.of(grant(groupA, "VIEW"), grant(groupA, "OPERATE"))));
    String id = created.get("id").asText();
    assertThat(created.get("grants")).hasSize(1);
    assertThat(created.get("grants").get(0).get("level").asText()).isEqualTo("OPERATE");

    assertThat(
            call(sessionCookie, HttpMethod.DELETE, "/api/v1/user-groups/" + id, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            call(sessionCookie, HttpMethod.GET, "/api/v1/user-groups/" + id, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void usersChangeTheirOwnPasswordAndAdminsCanResetIt() throws Exception {
    String username = unique("clave");
    String userId = createUser(username, List.of());
    String cookie = loginAs(username, USER_PASSWORD);

    assertThat(
            errorCode(
                call(
                    cookie,
                    HttpMethod.POST,
                    "/api/v1/auth/password",
                    Map.of("currentPassword", "mal-mal-mal", "newPassword", "nueva-clave-1"))))
        .isEqualTo("INVALID_CURRENT_PASSWORD");
    assertThat(
            call(
                    cookie,
                    HttpMethod.POST,
                    "/api/v1/auth/password",
                    Map.of("currentPassword", USER_PASSWORD, "newPassword", "nueva-clave-1"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(loginAs(username, USER_PASSWORD)).isNull();
    assertThat(loginAs(username, "nueva-clave-1")).isNotNull();

    assertThat(
            call(
                    sessionCookie,
                    HttpMethod.POST,
                    "/api/v1/users/" + userId + "/password",
                    Map.of("password", "otra-clave-22"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(loginAs(username, "otra-clave-22")).isNotNull();
  }

  @Test
  void usernamesMustBeUniqueAndValid() throws Exception {
    String username = unique("unico");
    createUser(username, List.of());

    assertThat(
            errorCode(
                call(
                    sessionCookie,
                    HttpMethod.POST,
                    "/api/v1/users",
                    Map.of(
                        "username",
                        username.toUpperCase(),
                        "displayName",
                        "Dup",
                        "password",
                        USER_PASSWORD))))
        .isEqualTo("USERNAME_EXISTS");
    assertThat(
            call(
                    sessionCookie,
                    HttpMethod.POST,
                    "/api/v1/users",
                    Map.of("username", "a b", "displayName", "Mal", "password", USER_PASSWORD))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void webSocketsHonorPermissionsAndGroupScope() throws Exception {
    String groupA = group();
    String groupB = group();
    String playerA = player(groupA);
    String playerB = player(groupB);
    String scheduler = userCookie(SCHEDULER_PERMISSIONS, List.of(grant(groupA, "OPERATE")));
    String viewer = userCookie(VIEWER_PERMISSIONS, List.of(grant(groupA, "OPERATE")));
    String terminalUser =
        userCookie(List.of("PLAYER_TERMINAL", "PLAYER_VIEW"), List.of(grant(groupA, "OPERATE")));

    connect("/ws/remote/" + playerA, scheduler).close();
    assertThatThrownBy(() -> connect("/ws/remote/" + playerB, scheduler))
        .isInstanceOf(ExecutionException.class);
    assertThatThrownBy(() -> connect("/ws/remote/" + playerA, viewer))
        .isInstanceOf(ExecutionException.class);
    assertThatThrownBy(() -> connect("/ws/remote/" + playerA, null))
        .isInstanceOf(ExecutionException.class);
    connect("/ws/remote/" + playerB, sessionCookie).close();

    assertThatThrownBy(() -> connect("/ws/shell/" + playerA, scheduler))
        .isInstanceOf(ExecutionException.class);
    connect("/ws/shell/" + playerA, terminalUser).close();
    assertThatThrownBy(() -> connect("/ws/shell/" + playerB, terminalUser))
        .isInstanceOf(ExecutionException.class);
    connect("/ws/shell/" + playerB, sessionCookie).close();
  }

  @Test
  void aPlayerOnlyChangesGroupWithOperateOnTheTarget() throws Exception {
    String groupA = group();
    String groupB = group();
    String playerA = player(groupA);
    String manager =
        userCookie(List.of("PLAYER_VIEW", "PLAYER_MANAGE"), List.of(grant(groupA, "OPERATE")));

    assertThat(
            call(manager, HttpMethod.PATCH, "/api/v1/players/" + playerA, Map.of("groupId", groupB))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    manager,
                    HttpMethod.PATCH,
                    "/api/v1/players/" + playerA,
                    Map.of("clearGroup", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    manager,
                    HttpMethod.PATCH,
                    "/api/v1/players/" + playerA,
                    Map.of("name", unique("nuevo")))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }
}
