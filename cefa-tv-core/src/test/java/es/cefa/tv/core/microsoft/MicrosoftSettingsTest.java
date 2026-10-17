package es.cefa.tv.core.microsoft;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.microsoft.dto.MicrosoftIdentity;
import es.cefa.tv.core.shared.config.CoreProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** Pruebas de la activación de Microsoft y de la lectura de reclamaciones del token. */
class MicrosoftSettingsTest {

  private static final String TENANT = "0a1b2c3d-1111-2222-3333-444455556666";

  private static boolean enabled(String tenant, String clientId, String secret) {
    return new CoreProperties.Microsoft(tenant, clientId, secret, "", false, "").enabled();
  }

  @Test
  void isEnabledOnlyWithTenantGuidClientAndSecret() {
    assertThat(enabled(TENANT, "client", "secret")).isTrue();
  }

  @Test
  void isOffWhenAnyValueIsMissingOrAPlaceholder() {
    assertThat(enabled("", "client", "secret")).isFalse();
    assertThat(enabled(TENANT, "", "secret")).isFalse();
    assertThat(enabled(TENANT, "client", "")).isFalse();
    assertThat(enabled(TENANT, "changeme-client-id", "secret")).isFalse();
    assertThat(enabled(TENANT, "client", "changeme-client-secret")).isFalse();
    assertThat(enabled(null, "client", "secret")).isFalse();
  }

  @Test
  void isOffWhenTheTenantIsNotADirectoryGuid() {
    assertThat(enabled("common", "client", "secret")).isFalse();
    assertThat(enabled("organizations", "client", "secret")).isFalse();
    assertThat(enabled("contoso.onmicrosoft.com", "client", "secret")).isFalse();
  }

  @Test
  void usesTheSpringTemplateWhenNoRedirectUriIsConfigured() {
    assertThat(
            new CoreProperties.Microsoft(TENANT, "c", "s", " ", false, "").effectiveRedirectUri())
        .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
    assertThat(
            new CoreProperties.Microsoft(
                    TENANT,
                    "c",
                    "s",
                    "https://tv.cefa.es/core-api/login/oauth2/code/azure",
                    false,
                    "")
                .effectiveRedirectUri())
        .isEqualTo("https://tv.cefa.es/core-api/login/oauth2/code/azure");
  }

  private static DefaultOidcUser oidc(String email, String preferred, String upn, String oid) {
    OidcIdToken.Builder token =
        OidcIdToken.withTokenValue("token")
            .subject("sub-1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("name", "Ana Pérez");
    if (email != null) {
      token.claim("email", email);
    }
    if (preferred != null) {
      token.claim("preferred_username", preferred);
    }
    if (upn != null) {
      token.claim("upn", upn);
    }
    if (oid != null) {
      token.claim("oid", oid);
    }
    return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token.build());
  }

  @Test
  void takesTheEmailClaimFirstAndNormalizesIt() {
    MicrosoftIdentity identity =
        MicrosoftIdentity.from(oidc(" Ana@CEFA.es ", "otro@cefa.es", null, "oid-1"));

    assertThat(identity.email()).isEqualTo("ana@cefa.es");
    assertThat(identity.objectId()).isEqualTo("oid-1");
    assertThat(identity.displayName()).isEqualTo("Ana Pérez");
  }

  @Test
  void fallsBackToPreferredUsernameAndThenUpn() {
    assertThat(MicrosoftIdentity.from(oidc(null, "Ana@Cefa.es", "u@cefa.es", null)).email())
        .isEqualTo("ana@cefa.es");
    assertThat(MicrosoftIdentity.from(oidc(null, null, "UPN@cefa.es", null)).email())
        .isEqualTo("upn@cefa.es");
  }

  @Test
  void ignoresClaimsThatAreNotEmailAddresses() {
    assertThat(MicrosoftIdentity.from(oidc(null, "ana", null, null)).email()).isNull();
  }
}
