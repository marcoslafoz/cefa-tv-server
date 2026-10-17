package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Pruebas de las carpetas de la biblioteca de medios. */
class FolderFlowTest extends AbstractIntegrationTest {

  @BeforeEach
  void authenticate() {
    login();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private ResponseEntity<String> create(String cookie, String name, String parentId) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("parentId", parentId);
    return call(cookie, HttpMethod.POST, "/api/v1/folders", body);
  }

  private String folder(String name, String parentId) throws Exception {
    ResponseEntity<String> response = create(sessionCookie, name, parentId);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
    return mapper.readTree(response.getBody()).get("id").asText();
  }

  private ResponseEntity<String> patch(String id, Map<String, Object> body) {
    return call(sessionCookie, HttpMethod.PATCH, "/api/v1/folders/" + id, body);
  }

  private JsonNode folderInfo(String id) throws Exception {
    for (JsonNode folder : adminGet("/api/v1/folders")) {
      if (id.equals(folder.get("id").asText())) {
        return folder;
      }
    }
    throw new AssertionError("Carpeta no encontrada " + id);
  }

  private List<String> ids(JsonNode array) {
    List<String> ids = new java.util.ArrayList<>();
    array.forEach(item -> ids.add(item.get("id").asText()));
    return ids;
  }

  private String web(String name, String folderId) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("url", "https://example.org");
    body.put("folderId", folderId);
    return adminPost("/api/v1/media/web", body).get("id").asText();
  }

  @Test
  void theTreeShowsCountersAndBreadcrumbs() throws Exception {
    String root = folder(unique("raiz"), null);
    String child = folder(unique("hijo"), root);
    String grandchild = folder(unique("nieto"), child);
    web(unique("w1"), child);
    web(unique("w2"), child);

    JsonNode childInfo = folderInfo(child);
    assertThat(childInfo.get("parentId").asText()).isEqualTo(root);
    assertThat(childInfo.get("itemCount").asInt()).isEqualTo(2);
    assertThat(childInfo.get("subfolderCount").asInt()).isEqualTo(1);
    assertThat(childInfo.has("kind")).isFalse();
    assertThat(folderInfo(root).get("subfolderCount").asInt()).isEqualTo(1);
    assertThat(folderInfo(root).get("itemCount").asInt()).isZero();

    JsonNode path = adminGet("/api/v1/folders/" + grandchild + "/path");
    assertThat(path).hasSize(3);
    assertThat(path.get(0).get("id").asText()).isEqualTo(root);
    assertThat(path.get(2).get("id").asText()).isEqualTo(grandchild);
  }

  @Test
  void namesAreUniqueAmongSiblingsIgnoringCase() throws Exception {
    String name = unique("Cartelería");
    String parent = folder(unique("padre"), null);
    folder(name, parent);

    ResponseEntity<String> duplicate = create(sessionCookie, name.toUpperCase(), parent);
    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorCode(duplicate)).isEqualTo("FOLDER_NAME_EXISTS");

    folder(name, null);
    String other = folder(unique("otro"), parent);
    ResponseEntity<String> rename = patch(other, Map.of("name", name.toLowerCase()));
    assertThat(errorCode(rename)).isEqualTo("FOLDER_NAME_EXISTS");
    assertThat(patch(other, Map.of("name", unique("libre"))).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void depthIsLimitedAndFoldersCannotFormCycles() throws Exception {
    String top = folder(unique("n1"), null);
    String current = top;
    for (int level = 2; level <= 10; level++) {
      current = folder(unique("n" + level), current);
    }
    ResponseEntity<String> tooDeep = create(sessionCookie, unique("n11"), current);
    assertThat(errorCode(tooDeep)).isEqualTo("FOLDER_TOO_DEEP");

    String other = folder(unique("otra"), null);
    folder(unique("hija"), other);
    ResponseEntity<String> move = patch(other, Map.of("parentId", current));
    assertThat(errorCode(move)).isEqualTo("FOLDER_TOO_DEEP");

    String a = folder(unique("a"), null);
    String b = folder(unique("b"), a);
    String c = folder(unique("c"), b);
    assertThat(errorCode(patch(a, Map.of("parentId", c)))).isEqualTo("FOLDER_CYCLE");
    assertThat(errorCode(patch(a, Map.of("parentId", a)))).isEqualTo("FOLDER_CYCLE");
    assertThat(patch(c, Map.of("parentId", a)).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(patch(c, Map.of("moveToRoot", true)).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(folderInfo(c).get("parentId").isNull()).isTrue();
  }

  @Test
  void foldersAreOnlyForMediaAndMustExist() throws Exception {
    String unknown = UUID.randomUUID().toString();

    assertThat(create(sessionCookie, unique("x"), unknown).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(patch(folder(unique("f"), null), Map.of("parentId", unknown)).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(
                    sessionCookie,
                    HttpMethod.POST,
                    "/api/v1/media/web",
                    Map.of("name", unique("w"), "url", "https://example.org", "folderId", unknown))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(sessionCookie, HttpMethod.GET, "/api/v1/folders/" + unknown + "/path", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    String folder = folder(unique("medios"), null);
    ResponseEntity<String> playlist =
        call(
            sessionCookie,
            HttpMethod.POST,
            "/api/v1/playlists",
            Map.of("name", unique("p"), "items", List.of(), "folderId", folder));
    assertThat(playlist.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode created = mapper.readTree(playlist.getBody());
    assertThat(created.has("folderId")).isFalse();
    assertThat(created.has("folderPath")).isFalse();
    assertThat(
            call(
                    sessionCookie,
                    HttpMethod.POST,
                    "/api/v1/playlists/move",
                    Map.of("ids", List.of(created.get("id").asText())))
                .getStatusCode()
                .value())
        .isIn(404, 405);
    assertThat(
            call(sessionCookie, HttpMethod.GET, "/api/v1/playlists?folderId=" + folder, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void deletingRequiresAnEmptyFolderUnlessContentsMoveUp() throws Exception {
    String parent = folder(unique("padre"), null);
    String folder = folder(unique("carpeta"), parent);
    String sub = folder(unique("sub"), folder);
    String mediaId = web(unique("web"), folder);

    ResponseEntity<String> blocked =
        call(sessionCookie, HttpMethod.DELETE, "/api/v1/folders/" + folder, null);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorCode(blocked)).isEqualTo("FOLDER_NOT_EMPTY");

    ResponseEntity<String> moved =
        call(
            sessionCookie,
            HttpMethod.DELETE,
            "/api/v1/folders/" + folder + "?moveContentsToParent=true",
            null);
    assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(adminGet("/api/v1/media/" + mediaId).get("folderId").asText()).isEqualTo(parent);
    assertThat(folderInfo(sub).get("parentId").asText()).isEqualTo(parent);

    String empty = folder(unique("vacia"), null);
    assertThat(
            call(sessionCookie, HttpMethod.DELETE, "/api/v1/folders/" + empty, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void deletingWithAClashingSubfolderNameIsRejectedWithoutChanges() throws Exception {
    String parent = folder(unique("padre"), null);
    String clash = unique("repetida");
    folder(clash, parent);
    String middle = folder(unique("medio"), parent);
    String inner = folder(clash, middle);

    ResponseEntity<String> response =
        call(
            sessionCookie,
            HttpMethod.DELETE,
            "/api/v1/folders/" + middle + "?moveContentsToParent=true",
            null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorCode(response)).isEqualTo("FOLDER_NAME_EXISTS");
    assertThat(folderInfo(inner).get("parentId").asText()).isEqualTo(middle);
  }

  @Test
  void mediaListingsFilterByFolderRecursivelyAndSearchGlobally() throws Exception {
    String marker = unique("buscame");
    String top = folder(unique("top"), null);
    String sub = folder(unique("sub"), top);
    String inTop = web(marker + "-top", top);
    String inSub = web(marker + "-sub", sub);
    String atRoot = web(marker + "-raiz", null);
    String uploaded = uploadImage(marker + "-img.png", sub).get("id").asText();

    assertThat(ids(adminGet("/api/v1/media?folderId=" + top + "&size=100").get("content")))
        .containsExactly(inTop);
    assertThat(
            ids(
                adminGet("/api/v1/media?folderId=" + top + "&recursive=true&size=100")
                    .get("content")))
        .containsExactlyInAnyOrder(inTop, inSub, uploaded);
    assertThat(
            ids(adminGet("/api/v1/media?folderId=root&q=" + marker + "&size=100").get("content")))
        .containsExactly(atRoot);

    JsonNode global = adminGet("/api/v1/media?q=" + marker + "&size=100").get("content");
    assertThat(ids(global)).containsExactlyInAnyOrder(inTop, inSub, atRoot, uploaded);
    for (JsonNode media : global) {
      if (inSub.equals(media.get("id").asText())) {
        assertThat(media.get("folderPath")).hasSize(2);
        assertThat(media.get("folderPath").get(0).get("id").asText()).isEqualTo(top);
        assertThat(media.get("folderId").asText()).isEqualTo(sub);
      }
      if (atRoot.equals(media.get("id").asText())) {
        assertThat(media.get("folderPath")).isEmpty();
      }
    }
    assertThat(
            call(sessionCookie, HttpMethod.GET, "/api/v1/media?folderId=no-es-un-uuid", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void bulkMovesAndSingleMovesUpdateTheFolder() throws Exception {
    String target = folder(unique("destino"), null);
    String one = web(unique("a"), null);
    String two = web(unique("b"), null);

    JsonNode moved =
        adminPost("/api/v1/media/move", Map.of("ids", List.of(one, two), "folderId", target));
    assertThat(moved.get("moved").asInt()).isEqualTo(2);
    assertThat(adminGet("/api/v1/media/" + one).get("folderId").asText()).isEqualTo(target);
    assertThat(folderInfo(target).get("itemCount").asInt()).isEqualTo(2);

    assertThat(
            call(
                    sessionCookie,
                    HttpMethod.PATCH,
                    "/api/v1/media/" + one,
                    Map.of("moveToRoot", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(adminGet("/api/v1/media/" + one).get("folderId").isNull()).isTrue();

    Map<String, Object> toRoot = new HashMap<>();
    toRoot.put("ids", List.of(two));
    toRoot.put("folderId", null);
    assertThat(adminPost("/api/v1/media/move", toRoot).get("moved").asInt()).isEqualTo(1);

    assertThat(
            call(
                    sessionCookie,
                    HttpMethod.POST,
                    "/api/v1/media/move",
                    Map.of("ids", List.of(UUID.randomUUID().toString()), "folderId", target))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(
                    sessionCookie,
                    HttpMethod.POST,
                    "/api/v1/media/move",
                    Map.of("ids", List.of(one), "folderId", UUID.randomUUID().toString()))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void movingAFolderKeepsPlaylistReferencesToItsMedia() throws Exception {
    String folder = folder(unique("origen"), null);
    String destination = folder(unique("destino"), null);
    String assetId = uploadImage(unique("img") + ".png", folder).get("id").asText();
    String playlistId =
        adminPost(
                "/api/v1/playlists",
                Map.of(
                    "name",
                    unique("lista"),
                    "items",
                    List.of(
                        Map.of(
                            "assetId", assetId, "durationSec", 10, "fit", "COVER", "muted", true))))
            .get("id")
            .asText();

    assertThat(patch(folder, Map.of("parentId", destination)).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    JsonNode playlist = adminGet("/api/v1/playlists/" + playlistId);
    assertThat(playlist.get("items").get(0).get("asset").get("id").asText()).isEqualTo(assetId);
    assertThat(adminGet("/api/v1/media/" + assetId).get("folderId").asText()).isEqualTo(folder);
  }

  @Test
  void onlyMediaManagersManageFolders() throws Exception {
    String viewer = userCookie(VIEWER_PERMISSIONS, List.of());
    assertThat(call(viewer, HttpMethod.GET, "/api/v1/folders", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(create(viewer, unique("x"), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    String editor = userCookie(List.of("PLAYLIST_MANAGE"), List.of());
    ResponseEntity<String> denied = create(editor, unique("m"), null);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(errorCode(denied)).isEqualTo("PERMISSION_DENIED");
    String existing = folder(unique("md"), null);
    assertThat(call(editor, HttpMethod.DELETE, "/api/v1/folders/" + existing, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(editor, HttpMethod.PATCH, "/api/v1/folders/" + existing, Map.of("name", "x"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    String mediaEditor = userCookie(List.of("MEDIA_MANAGE"), List.of());
    ResponseEntity<String> created = create(mediaEditor, unique("ok"), null);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String createdId = mapper.readTree(created.getBody()).get("id").asText();
    assertThat(
            call(
                    mediaEditor,
                    HttpMethod.PATCH,
                    "/api/v1/folders/" + createdId,
                    Map.of("name", unique("r")))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call(mediaEditor, HttpMethod.DELETE, "/api/v1/folders/" + createdId, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    String scheduler = userCookie(SCHEDULER_PERMISSIONS, List.of());
    assertThat(create(scheduler, unique("a"), null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void folderChangesAreAudited() throws Exception {
    String parent = folder(unique("padre"), null);
    String name = unique("auditada");
    String id = folder(name, parent);
    String renamed = name + "-v2";
    patch(id, Map.of("name", renamed));
    patch(id, Map.of("moveToRoot", true));
    String media = web(unique("w"), id);
    adminPost("/api/v1/media/move", Map.of("ids", List.of(media), "folderId", parent));
    call(sessionCookie, HttpMethod.DELETE, "/api/v1/folders/" + id, null);

    List<String> actions =
        adminGet("/api/v1/audit?size=200&entityId=" + id).get("content").findValuesAsText("action");
    assertThat(actions)
        .containsExactlyInAnyOrder(
            "FOLDER_CREATE", "FOLDER_RENAME", "FOLDER_MOVE", "FOLDER_DELETE");
    JsonNode rename =
        adminGet("/api/v1/audit?size=10&action=FOLDER_RENAME&entityId=" + id).get("content").get(0);
    assertThat(rename.get("details").get("changes").get(0).get("after").asText())
        .isEqualTo(renamed);
    assertThat(adminGet("/api/v1/audit?size=10&action=MEDIA_MOVE").get("content")).isNotEmpty();
    assertThat(adminGet("/api/v1/audit/actions").findValuesAsText("action"))
        .contains("MEDIA_MOVE")
        .doesNotContain("PLAYLIST_MOVE");
  }
}
