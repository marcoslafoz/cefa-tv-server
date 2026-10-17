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

/**
 * Sin credenciales de Microsoft la aplicación arranca, el flujo OAuth no existe y los usuarios solo
 * de Microsoft se pueden preparar pero no entran por formulario.
 */
class MicrosoftDisabledTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "password-1234";

  @BeforeEach
  void authenticate() {
    login();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private Map<String, Object> body(
      String username, String provider, String email, String password) {
    Map<String, Object> body = new HashMap<>();
    body.put("username", username);
    body.put("displayName", username);
    if (provider != null) {
      body.put("authProvider", provider);
    }
    if (email != null) {
      body.put("email", email);
    }
    if (password != null) {
      body.put("password", password);
    }
    return body;
  }

  private ResponseEntity<String> create(Map<String, Object> body) {
    return call(sessionCookie, HttpMethod.POST, "/api/v1/users", body);
  }

  @Test
  void providersAreOpenAndReportMicrosoftAsOff() throws Exception {
    ResponseEntity<String> response =
        rest.getForEntity(url("/api/v1/auth/providers"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode json = mapper.readTree(response.getBody());
    assertThat(json.get("local").asBoolean()).isTrue();
    assertThat(json.get("microsoft").asBoolean()).isFalse();
  }

  @Test
  void theOauthFlowDoesNotExistWithoutCredentials() {
    ResponseEntity<String> response =
        rest.getForEntity(url("/oauth2/authorization/azure"), String.class);

    assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    assertThat(response.getHeaders().getLocation()).isNull();
  }

  @Test
  void aMicrosoftOnlyUserCanBePreparedButCannotUseTheForm() throws Exception {
    String username = unique("ms");
    ResponseEntity<String> created =
        create(body(username, "MICROSOFT", username + "@cefa.es", null));

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode json = mapper.readTree(created.getBody());
    assertThat(json.get("authProvider").asText()).isEqualTo("MICROSOFT");
    assertThat(json.get("email").asText()).isEqualTo(username + "@cefa.es");
    assertThat(json.get("microsoftLinked").asBoolean()).isFalse();
    assertThat(loginAs(username, PASSWORD)).isNull();
    assertThat(loginAs(username, "")).isNull();
  }

  @Test
  void microsoftUsersNeedAnEmailAndCannotHaveAPassword() throws Exception {
    ResponseEntity<String> noEmail = create(body(unique("ms"), "MICROSOFT", null, null));
    ResponseEntity<String> withPassword =
        create(body(unique("ms"), "MICROSOFT", "x@cefa.es", PASSWORD));

    assertThat(noEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(noEmail)).isEqualTo("EMAIL_REQUIRED");
    assertThat(withPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(withPassword)).isEqualTo("PASSWORD_NOT_ALLOWED");
  }

  @Test
  void localUsersStillNeedAPassword() throws Exception {
    ResponseEntity<String> response = create(body(unique("local"), null, null, null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(response)).isEqualTo("PASSWORD_REQUIRED");
  }

  @Test
  void aHybridUserLogsInWithPasswordAndMeShowsTheProvider() throws Exception {
    String username = unique("hybrid");
    create(body(username, "HYBRID", username + "@cefa.es", PASSWORD));

    String cookie = loginAs(username, PASSWORD);

    assertThat(cookie).isNotNull();
    ResponseEntity<String> me = call(cookie, HttpMethod.GET, "/api/v1/auth/me", null);
    JsonNode json = mapper.readTree(me.getBody());
    assertThat(json.get("authProvider").asText()).isEqualTo("HYBRID");
    assertThat(json.get("email").asText()).isEqualTo(username + "@cefa.es");
  }

  @Test
  void emailsAreUniqueIgnoringCase() throws Exception {
    String username = unique("dup");
    create(body(username, "HYBRID", username + "@cefa.es", PASSWORD));

    ResponseEntity<String> duplicate =
        create(body(unique("dup"), "HYBRID", username.toUpperCase() + "@CEFA.ES", PASSWORD));

    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorCode(duplicate)).isEqualTo("EMAIL_EXISTS");
  }

  @Test
  void passwordOperationsAreRejectedForMicrosoftUsers() throws Exception {
    String username = unique("ms");
    String id =
        mapper
            .readTree(create(body(username, "MICROSOFT", username + "@cefa.es", null)).getBody())
            .get("id")
            .asText();

    ResponseEntity<String> reset =
        call(
            sessionCookie,
            HttpMethod.POST,
            "/api/v1/users/" + id + "/password",
            Map.of("password", PASSWORD));

    assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(reset)).isEqualTo("PASSWORD_NOT_ALLOWED");
  }

  @Test
  void switchingFromMicrosoftToPasswordRequiresOneAndThenWorks() throws Exception {
    String username = unique("ms");
    String id =
        mapper
            .readTree(create(body(username, "MICROSOFT", username + "@cefa.es", null)).getBody())
            .get("id")
            .asText();
    Map<String, Object> update = new HashMap<>();
    update.put("displayName", username);
    update.put("enabled", true);
    update.put("authProvider", "HYBRID");

    ResponseEntity<String> withoutPassword =
        call(sessionCookie, HttpMethod.PUT, "/api/v1/users/" + id, update);
    update.put("password", PASSWORD);
    ResponseEntity<String> withPassword =
        call(sessionCookie, HttpMethod.PUT, "/api/v1/users/" + id, update);

    assertThat(withoutPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorCode(withoutPassword)).isEqualTo("PASSWORD_REQUIRED");
    assertThat(withPassword.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(loginAs(username, PASSWORD)).isNotNull();
  }

  @Test
  void updatesWithoutProviderOrEmailKeepTheCurrentOnes() throws Exception {
    String username = unique("keep");
    String id =
        mapper
            .readTree(create(body(username, "HYBRID", username + "@cefa.es", PASSWORD)).getBody())
            .get("id")
            .asText();
    Map<String, Object> update = new HashMap<>();
    update.put("displayName", "Nuevo nombre");
    update.put("enabled", true);

    ResponseEntity<String> response =
        call(sessionCookie, HttpMethod.PUT, "/api/v1/users/" + id, update);

    JsonNode json = mapper.readTree(response.getBody());
    assertThat(json.get("authProvider").asText()).isEqualTo("HYBRID");
    assertThat(json.get("email").asText()).isEqualTo(username + "@cefa.es");
    assertThat(loginAs(username, PASSWORD)).isNotNull();
  }
}
