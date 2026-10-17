package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Flujo completo: alta por emparejamiento, manifest, descarga de ficheros, heartbeat, ack y entrega
 * diferida.
 */
class DeliveryFlowTest extends AbstractIntegrationTest {

  @BeforeEach
  void authenticate() {
    login();
  }

  private JsonNode schedulePlaylist(String playerId, String assetId, String name) throws Exception {
    JsonNode playlist =
        adminPost(
            "/api/v1/playlists",
            Map.of("name", name, "items", List.of(Map.of("assetId", assetId, "durationSec", 5))));
    return adminPost(
        "/api/v1/schedules",
        Map.of(
            "name",
            name,
            "playlistId",
            playlist.get("id").asText(),
            "allDay",
            true,
            "targets",
            Map.of("playerIds", List.of(playerId))));
  }

  @Test
  void enrollManifestFilesHeartbeatAndAck() throws Exception {
    JsonNode asset = uploadImage("logo.png");
    JsonNode enrolled = enroll("pantalla-flow");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();

    schedulePlaylist(playerId, asset.get("id").asText(), "Flujo");

    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> fetchManifest(token).get("version").asLong() >= 2);
    JsonNode manifest = fetchManifest(token);
    assertThat(manifest.get("schedules")).hasSize(1);
    assertThat(manifest.get("files")).hasSize(1);
    String sha = manifest.get("files").get(0).get("sha256").asText();

    HttpHeaders headers = playerHeaders(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<byte[]> zip =
        rest.exchange(
            url("/player-api/files"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of("sha256", List.of(sha)), headers),
            byte[].class);
    Map<String, Integer> entries = new HashMap<>();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip.getBody()))) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        entries.put(entry.getName(), in.readAllBytes().length);
      }
    }
    assertThat(entries).containsOnlyKeys(sha);

    HttpHeaders rangeHeaders = playerHeaders(token);
    rangeHeaders.set(HttpHeaders.RANGE, "bytes=0-9");
    ResponseEntity<byte[]> partial =
        rest.exchange(
            url("/player-api/files/" + sha),
            HttpMethod.GET,
            new HttpEntity<>(rangeHeaders),
            byte[].class);
    assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
    assertThat(partial.getBody()).hasSize(10);

    long desired = manifest.get("version").asLong();
    JsonNode heartbeat =
        playerPost(
            token,
            "/player-api/heartbeat",
            Map.of("appliedVersion", 0, "downloadingVersion", desired, "state", "DOWNLOADING"));
    assertThat(heartbeat.get("desiredVersion").asLong()).isEqualTo(desired);
    assertThat(adminGet("/api/v1/players/" + playerId).get("pending").asBoolean()).isTrue();

    playerPost(token, "/player-api/ack", Map.of("version", desired, "status", "APPLIED"));
    JsonNode player = adminGet("/api/v1/players/" + playerId);
    assertThat(player.get("pending").asBoolean()).isFalse();
    assertThat(player.get("appliedVersion").asLong()).isEqualTo(desired);
    assertThat(
            adminGet("/api/v1/players/" + playerId + "/deliveries").get(0).get("status").asText())
        .isEqualTo("APPLIED");
  }

  @Test
  void changesWhileOfflineStayPendingUntilThePlayerReportsBack() throws Exception {
    JsonNode asset = uploadImage("offline.png");
    JsonNode enrolled = enroll("pantalla-offline");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();

    playerPost(token, "/player-api/heartbeat", Map.of("appliedVersion", 1, "state", "IDLE"));
    assertThat(adminGet("/api/v1/players/" + playerId).get("pending").asBoolean()).isFalse();

    schedulePlaylist(playerId, asset.get("id").asText(), "Mientras estaba apagado");
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> adminGet("/api/v1/players/" + playerId).get("pending").asBoolean());

    JsonNode pending = adminGet("/api/v1/players/" + playerId);
    assertThat(pending.get("desiredVersion").asLong())
        .isGreaterThan(pending.get("appliedVersion").asLong());

    HttpHeaders headers = adminHeaders();
    rest.exchange(
        url("/api/v1/players/" + playerId + "/restart"),
        HttpMethod.POST,
        new HttpEntity<>(headers),
        String.class);

    JsonNode back =
        playerPost(token, "/player-api/heartbeat", Map.of("appliedVersion", 1, "state", "IDLE"));
    assertThat(back.get("desiredVersion").asLong())
        .isEqualTo(pending.get("desiredVersion").asLong());
    assertThat(back.get("commands").get(0).get("type").asText()).isEqualTo("RESTART");
    assertThat(
            playerPost(token, "/player-api/heartbeat", Map.of("appliedVersion", 1)).get("commands"))
        .isEmpty();

    playerPost(
        token,
        "/player-api/heartbeat",
        Map.of("appliedVersion", pending.get("desiredVersion").asLong(), "state", "PLAYING"));
    assertThat(adminGet("/api/v1/players/" + playerId).get("pending").asBoolean()).isFalse();
  }

  @Test
  void unauthenticatedRequestsAreRejected() throws Exception {
    ResponseEntity<String> noToken = rest.getForEntity(url("/player-api/manifest"), String.class);
    assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    ResponseEntity<String> noCookie = rest.getForEntity(url("/api/v1/players"), String.class);
    assertThat(noCookie.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void mediaInUseCannotBeDeleted() throws Exception {
    JsonNode asset = uploadImage("usado.png");
    JsonNode enrolled = enroll("pantalla-borrado");
    schedulePlaylist(enrolled.get("playerId").asText(), asset.get("id").asText(), "Usa el medio");

    ResponseEntity<String> response =
        rest.exchange(
            url("/api/v1/media/" + asset.get("id").asText()),
            HttpMethod.DELETE,
            new HttpEntity<>(adminHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }
}
