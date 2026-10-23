package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Pruebas del volumen de un reproductor: valor por defecto, cambio, límites y entrega. */
class PlayerVolumeFlowTest extends AbstractIntegrationTest {

  @BeforeEach
  void authenticate() {
    login();
  }

  private ResponseEntity<String> patchPlayer(String id, Map<String, ?> body) {
    return call(sessionCookie, HttpMethod.PATCH, "/api/v1/players/" + id, body);
  }

  private JsonNode heartbeatConfig(String token) throws Exception {
    Map<String, Object> heartbeat = new HashMap<>();
    heartbeat.put("appliedVersion", 0);
    heartbeat.put("state", "IDLE");
    return playerPost(token, "/player-api/heartbeat", heartbeat).get("config");
  }

  @Test
  void aNewPlayerStartsAtFullVolume() throws Exception {
    JsonNode enrolled = enroll("pantalla-" + UUID.randomUUID().toString().substring(0, 8));

    assertThat(
            adminGet("/api/v1/players/" + enrolled.get("playerId").asText()).get("volume").asInt())
        .isEqualTo(100);
    assertThat(heartbeatConfig(enrolled.get("deviceToken").asText()).get("volume").asInt())
        .isEqualTo(100);
  }

  @Test
  void theVolumeIsChangedAndDeliveredToThePlayer() throws Exception {
    JsonNode enrolled = enroll("pantalla-" + UUID.randomUUID().toString().substring(0, 8));
    String id = enrolled.get("playerId").asText();

    ResponseEntity<String> response = patchPlayer(id, Map.of("volume", 35));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(mapper.readTree(response.getBody()).get("volume").asInt()).isEqualTo(35);
    assertThat(heartbeatConfig(enrolled.get("deviceToken").asText()).get("volume").asInt())
        .isEqualTo(35);
  }

  @Test
  void aVolumeOutOfRangeIsRejected() throws Exception {
    String id =
        enroll("pantalla-" + UUID.randomUUID().toString().substring(0, 8)).get("playerId").asText();

    assertThat(patchPlayer(id, Map.of("volume", 101)).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(patchPlayer(id, Map.of("volume", -1)).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
