package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flujo completo de emparejamiento por código, incluidos caducidad, secreto y límite de intentos.
 */
class PairingFlowTest extends AbstractIntegrationTest {

  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void authenticate() {
    login();
  }

  private JsonNode requestPairing(String hostname) throws IOException {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> response =
        rest.exchange(
            url("/player-api/pairing/request"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of("hostname", hostname), headers),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return mapper.readTree(response.getBody());
  }

  private ResponseEntity<String> poll(JsonNode pairing, String secret) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Pairing-Secret", secret);
    return rest.exchange(
        url("/player-api/pairing/" + pairing.get("pairingId").asText()),
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);
  }

  private JsonNode pollStatus(JsonNode pairing) throws IOException {
    return mapper.readTree(poll(pairing, pairing.get("secret").asText()).getBody());
  }

  private ResponseEntity<String> claim(String cookie, String code, String groupId) {
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("code", code);
    body.put("groupId", groupId);
    return call(cookie, HttpMethod.POST, "/api/v1/pairings/claim", body);
  }

  @Test
  void pairingHandsOutTheCodeThenCredentialsAfterAnAdminClaimsIt() throws Exception {
    JsonNode pairing = requestPairing("pantalla-pairing");
    assertThat(pairing.get("code").asText()).matches("[A-Z0-9]{3}-[A-Z0-9]{3}");
    assertThat(pairing.get("secret").asText()).isNotBlank();
    assertThat(pollStatus(pairing).get("status").asText()).isEqualTo("PENDING");

    JsonNode pending = adminGet("/api/v1/pairings/pending");
    assertThat(pending.toString()).contains(pairing.get("pairingId").asText());
    assertThat(pending.toString()).doesNotContain(pairing.get("code").asText());

    ResponseEntity<String> claimed =
        claim(sessionCookie, pairing.get("code").asText().toLowerCase(), null);
    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(mapper.readTree(claimed.getBody()).get("hostname").asText())
        .isEqualTo("pantalla-pairing");

    JsonNode approved = pollStatus(pairing);
    assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
    assertThat(approved.get("deviceToken").asText()).isNotBlank();
    assertThat(approved.get("playerId").asText())
        .isEqualTo(mapper.readTree(claimed.getBody()).get("id").asText());
    assertThat(fetchManifest(approved.get("deviceToken").asText()).get("playerId").asText())
        .isEqualTo(approved.get("playerId").asText());
  }

  @Test
  void aWrongSecretIsRejectedAndNeverLeaksCredentials() throws Exception {
    JsonNode pairing = requestPairing("pantalla-secret");
    claim(sessionCookie, pairing.get("code").asText(), null);

    ResponseEntity<String> response = poll(pairing, "otro-secreto");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).doesNotContain("deviceToken");
    assertThat(poll(pairing, "").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void unknownPairingsLookExpired() throws Exception {
    JsonNode unknown = mapper.createObjectNode().put("pairingId", UUID.randomUUID().toString());

    ResponseEntity<String> response = poll(unknown, "x");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(mapper.readTree(response.getBody()).get("status").asText()).isEqualTo("EXPIRED");
  }

  @Test
  void aLostApprovalResponseDoesNotStrandThePlayer() throws Exception {
    JsonNode pairing = requestPairing("pantalla-lost");
    claim(sessionCookie, pairing.get("code").asText(), null);

    String first = pollStatus(pairing).get("deviceToken").asText();
    String second = pollStatus(pairing).get("deviceToken").asText();

    assertThat(second).isNotEqualTo(first);
    assertThat(
            rest.exchange(
                    url("/player-api/manifest"),
                    HttpMethod.GET,
                    new HttpEntity<>(playerHeaders(first)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    playerPost(second, "/player-api/heartbeat", Map.of("appliedVersion", 0, "state", "IDLE"));
    assertThat(pollStatus(pairing).get("status").asText()).isEqualTo("EXPIRED");
  }

  @Test
  void anExpiredCodeCannotBeClaimedAndANewOneWorks() throws Exception {
    JsonNode stale = requestPairing("pantalla-stale");
    jdbc.update(
        "update pairings set expires_at = now() - interval '1 minute' where id = ?::uuid",
        stale.get("pairingId").asText());

    ResponseEntity<String> claimStale = claim(sessionCookie, stale.get("code").asText(), null);
    assertThat(claimStale.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(errorCode(claimStale)).isEqualTo("INVALID_PAIRING_CODE");
    assertThat(pollStatus(stale).get("status").asText()).isEqualTo("EXPIRED");

    JsonNode fresh = requestPairing("pantalla-stale");
    assertThat(fresh.get("code").asText()).isNotEqualTo(stale.get("code").asText());
    assertThat(claim(sessionCookie, fresh.get("code").asText(), null).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(pollStatus(fresh).get("status").asText()).isEqualTo("APPROVED");
  }

  @Test
  void requestingAgainFromTheSameOriginSupersedesThePreviousCode() throws Exception {
    JsonNode first = requestPairing("pantalla-again");
    JsonNode second = requestPairing("pantalla-again");

    assertThat(pollStatus(first).get("status").asText()).isEqualTo("EXPIRED");
    assertThat(pollStatus(second).get("status").asText()).isEqualTo("PENDING");
    ResponseEntity<String> claimFirst = claim(sessionCookie, first.get("code").asText(), null);
    assertThat(claimFirst.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aClaimWithWrongCodesIsLimitedToFivePerMinutePerUser() throws Exception {
    String groupId =
        adminPost("/api/v1/groups", Map.of("name", "Limite " + UUID.randomUUID()))
            .get("id")
            .asText();
    String cookie =
        userCookie(
            java.util.List.of("PLAYER_MANAGE"),
            java.util.List.of(Map.of("playerGroupId", groupId, "level", "OPERATE")));

    for (int i = 0; i < 5; i++) {
      assertThat(claim(cookie, "ZZZ-ZZ" + i, groupId).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
    JsonNode pairing = requestPairing("pantalla-limit");
    ResponseEntity<String> blocked = claim(cookie, pairing.get("code").asText(), groupId);

    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(errorCode(blocked)).isEqualTo("TOO_MANY_ATTEMPTS");
    assertThat(pollStatus(pairing).get("status").asText()).isEqualTo("PENDING");
  }

  @Test
  void aNonAdminOnlyPairsIntoGroupsTheyCanOperate() throws Exception {
    String ownGroup =
        adminPost("/api/v1/groups", Map.of("name", "Propio " + UUID.randomUUID()))
            .get("id")
            .asText();
    String otherGroup =
        adminPost("/api/v1/groups", Map.of("name", "Ajeno " + UUID.randomUUID()))
            .get("id")
            .asText();
    String cookie =
        userCookie(
            java.util.List.of("PLAYER_MANAGE"),
            java.util.List.of(Map.of("playerGroupId", ownGroup, "level", "OPERATE")));
    JsonNode pairing = requestPairing("pantalla-scoped");

    assertThat(claim(cookie, pairing.get("code").asText(), otherGroup).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(claim(cookie, pairing.get("code").asText(), null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    ResponseEntity<String> ok = claim(cookie, pairing.get("code").asText(), ownGroup);
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(mapper.readTree(ok.getBody()).get("groupId").asText()).isEqualTo(ownGroup);
  }
}
