package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Con credenciales de Microsoft el flujo OAuth arranca y el resto de la API conserva su seguridad.
 */
@TestPropertySource(
    properties = {
      "cefa.tv.microsoft.tenant-id=0a1b2c3d-1111-2222-3333-444455556666",
      "cefa.tv.microsoft.client-id=test-client",
      "cefa.tv.microsoft.client-secret=test-secret",
      "cefa.tv.app-base-url=http://localhost:4000"
    })
class MicrosoftEnabledTest extends AbstractIntegrationTest {

  private static final String TENANT = "0a1b2c3d-1111-2222-3333-444455556666";

  private final HttpClient client =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  private HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void providersReportMicrosoftAsOn() throws Exception {
    ResponseEntity<String> response =
        rest.getForEntity(url("/api/v1/auth/providers"), String.class);

    JsonNode json = mapper.readTree(response.getBody());
    assertThat(json.get("local").asBoolean()).isTrue();
    assertThat(json.get("microsoft").asBoolean()).isTrue();
  }

  @Test
  void startingTheFlowRedirectsToTheDirectoryOfTheConfiguredTenant() throws Exception {
    HttpResponse<String> response =
        get("/oauth2/authorization/azure?redirect=/es/schedule&locale=es");

    assertThat(response.statusCode()).isEqualTo(302);
    String location = response.headers().firstValue("Location").orElseThrow();
    String decoded = URLDecoder.decode(location, StandardCharsets.UTF_8);
    assertThat(location)
        .startsWith("https://login.microsoftonline.com/" + TENANT + "/oauth2/v2.0/authorize?");
    assertThat(decoded)
        .contains("client_id=test-client")
        .contains("response_type=code")
        .contains("scope=openid profile email")
        .contains("prompt=select_account")
        .contains("/login/oauth2/code/azure")
        .contains("state=")
        .contains("nonce=");
    assertThat(response.headers().allValues("Set-Cookie"))
        .anyMatch(c -> c.startsWith("JSESSIONID"));
  }

  @Test
  void aCallbackWithAnUnknownStateGoesBackToTheLoginWithAGenericError() throws Exception {
    HttpResponse<String> response = get("/login/oauth2/code/azure?code=abc&state=desconocido");

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location").orElseThrow())
        .isEqualTo("http://localhost:4000/login?error=microsoft_failed");
    assertThat(response.headers().firstValue("Set-Cookie").orElse(""))
        .doesNotContain("ctv_session");
  }

  @Test
  void theRestOfTheApiKeepsRequiringTheSessionCookie() {
    ResponseEntity<String> response = rest.getForEntity(url("/api/v1/players"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
