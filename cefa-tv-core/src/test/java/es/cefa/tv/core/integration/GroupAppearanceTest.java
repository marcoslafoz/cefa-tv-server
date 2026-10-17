package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Pruebas del icono y el color de los grupos de reproductores. */
class GroupAppearanceTest extends AbstractIntegrationTest {

  @BeforeEach
  void authenticate() {
    login();
  }

  private ResponseEntity<String> create(Map<String, Object> body) {
    return call(sessionCookie, HttpMethod.POST, "/api/v1/groups", body);
  }

  private ResponseEntity<String> update(String cookie, String id, Map<String, Object> body) {
    return call(cookie, HttpMethod.PUT, "/api/v1/groups/" + id, body);
  }

  private JsonNode json(ResponseEntity<String> response) throws Exception {
    assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
    return mapper.readTree(response.getBody());
  }

  private JsonNode listed(String id) throws Exception {
    for (JsonNode group : adminGet("/api/v1/groups")) {
      if (id.equals(group.get("id").asText())) {
        return group;
      }
    }
    throw new AssertionError("Grupo no listado " + id);
  }

  @Test
  void aGroupHasAnOptionalIconAndColor() throws Exception {
    String name = uniqueName("tienda");
    JsonNode created =
        json(create(Map.of("name", name, "icon", "IconBuildingStore", "color", "blue")));
    String id = created.get("id").asText();

    assertThat(created.get("icon").asText()).isEqualTo("IconBuildingStore");
    assertThat(created.get("color").asText()).isEqualTo("blue");
    assertThat(listed(id).get("icon").asText()).isEqualTo("IconBuildingStore");
    assertThat(listed(id).get("color").asText()).isEqualTo("blue");

    JsonNode plain = json(create(Map.of("name", uniqueName("sin-adorno"))));
    assertThat(plain.get("icon").isNull()).isTrue();
    assertThat(plain.get("color").isNull()).isTrue();
  }

  @Test
  void theIconAndColorAreValidated() throws Exception {
    ResponseEntity<String> badIcon =
        create(Map.of("name", uniqueName("g"), "icon", "building-store"));
    assertThat(badIcon.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(badIcon)).isEqualTo("VALIDATION_ERROR");
    assertThat(create(Map.of("name", uniqueName("g"), "icon", "Icon Building")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            create(Map.of("name", uniqueName("g"), "icon", "Icon" + "A".repeat(70)))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    ResponseEntity<String> badColor = create(Map.of("name", uniqueName("g"), "color", "purple"));
    assertThat(badColor.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    for (String color :
        List.of("slate", "blue", "green", "amber", "red", "violet", "pink", "teal")) {
      assertThat(create(Map.of("name", uniqueName("g"), "color", color)).getStatusCode())
          .isEqualTo(HttpStatus.CREATED);
    }
  }

  @Test
  void updatesKeepWhatIsNotSentAndAnEmptyStringClears() throws Exception {
    String name = uniqueName("tienda");
    String id =
        json(create(Map.of("name", name, "icon", "IconBuildingStore", "color", "teal")))
            .get("id")
            .asText();

    JsonNode kept = json(update(sessionCookie, id, Map.of("name", name)));
    assertThat(kept.get("icon").asText()).isEqualTo("IconBuildingStore");
    assertThat(kept.get("color").asText()).isEqualTo("teal");

    JsonNode changed =
        json(update(sessionCookie, id, Map.of("name", name, "icon", "IconHome", "color", "red")));
    assertThat(changed.get("icon").asText()).isEqualTo("IconHome");
    assertThat(changed.get("color").asText()).isEqualTo("red");

    Map<String, Object> clear = new HashMap<>();
    clear.put("name", name);
    clear.put("icon", "");
    clear.put("color", "");
    JsonNode cleared = json(update(sessionCookie, id, clear));
    assertThat(cleared.get("icon").isNull()).isTrue();
    assertThat(cleared.get("color").isNull()).isTrue();
  }

  @Test
  void changingTheAppearanceNeedsPlayerManagementAndIsAudited() throws Exception {
    String name = uniqueName("aspecto");
    String groupId = json(create(Map.of("name", name))).get("id").asText();
    String scheduler =
        userCookie(
            SCHEDULER_PERMISSIONS, List.of(Map.of("playerGroupId", groupId, "level", "OPERATE")));
    String manager =
        userCookie(
            List.of("PLAYER_VIEW", "PLAYER_MANAGE"),
            List.of(Map.of("playerGroupId", groupId, "level", "OPERATE")));
    ResponseEntity<String> denied =
        update(scheduler, groupId, Map.of("name", name, "icon", "IconHome"));
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(denied)).isEqualTo("PERMISSION_DENIED");
    assertThat(update(scheduler, groupId, Map.of("name", name)).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    assertThat(
            update(manager, groupId, Map.of("name", name, "icon", "IconHome", "color", "amber"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    JsonNode entry =
        adminGet("/api/v1/audit?size=5&action=PLAYER_GROUP_UPDATE&entityId=" + groupId)
            .get("content")
            .get(0);
    List<String> fields = entry.get("details").get("changes").findValuesAsText("field");
    assertThat(fields).contains("icon", "color");
  }
}
