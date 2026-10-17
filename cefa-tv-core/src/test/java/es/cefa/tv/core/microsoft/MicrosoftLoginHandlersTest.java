package es.cefa.tv.core.microsoft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.auth.model.AuthProvider;
import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.security.SessionCookies;
import es.cefa.tv.core.auth.service.AuthService;
import es.cefa.tv.core.microsoft.dto.MicrosoftIdentity;
import es.cefa.tv.core.microsoft.dto.MicrosoftLoginResult;
import es.cefa.tv.core.microsoft.security.MicrosoftAuthorizationRequestResolver;
import es.cefa.tv.core.microsoft.security.MicrosoftLoginFailureHandler;
import es.cefa.tv.core.microsoft.security.MicrosoftLoginSuccessHandler;
import es.cefa.tv.core.microsoft.service.MicrosoftUserResolver;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** Pruebas de las redirecciones y de la cookie al terminar el flujo de Microsoft. */
class MicrosoftLoginHandlersTest {

  private static final String APP = "http://localhost:4000";

  private MicrosoftUserResolver resolver;
  private AuthService authService;
  private SessionCookies cookies;
  private AuditService audit;
  private MicrosoftLoginSuccessHandler success;
  private TestingAuthenticationToken authentication;

  @BeforeEach
  void setUp() {
    resolver = mock(MicrosoftUserResolver.class);
    authService = mock(AuthService.class);
    cookies = mock(SessionCookies.class);
    audit = mock(AuditService.class);
    success = new MicrosoftLoginSuccessHandler(resolver, authService, cookies, APP, audit);
    OidcIdToken token =
        OidcIdToken.withTokenValue("t")
            .subject("s")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("email", "ana@cefa.es")
            .claim("oid", "oid-1")
            .build();
    authentication =
        new TestingAuthenticationToken(
            new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token), null);
  }

  private static User acceptedUser() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setAuthProvider(AuthProvider.MICROSOFT);
    return user;
  }

  private void accept() {
    User user = acceptedUser();
    when(resolver.resolve(ArgumentMatchers.any(MicrosoftIdentity.class)))
        .thenReturn(MicrosoftLoginResult.accepted(user));
    when(authService.issueToken(user)).thenReturn("jwt");
    when(cookies.create("jwt")).thenReturn(ResponseCookie.from("ctv_session", "jwt").build());
  }

  @Test
  void issuesTheSessionCookieAndRedirectsToTheRequestedPath() throws IOException {
    accept();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request
        .getSession()
        .setAttribute(MicrosoftAuthorizationRequestResolver.TARGET_ATTRIBUTE, "/es/schedule");
    MockHttpServletResponse response = new MockHttpServletResponse();

    success.onAuthenticationSuccess(request, response, authentication);

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith("ctv_session=jwt");
    assertThat(response.getRedirectedUrl()).isEqualTo(APP + "/es/schedule");
    assertThat(request.getSession(false)).isNull();
  }

  @Test
  void ignoresATamperedTargetAndGoesToTheDashboard() throws IOException {
    accept();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request
        .getSession()
        .setAttribute(MicrosoftAuthorizationRequestResolver.TARGET_ATTRIBUTE, "//evil.example");
    MockHttpServletResponse response = new MockHttpServletResponse();

    success.onAuthenticationSuccess(request, response, authentication);

    assertThat(response.getRedirectedUrl()).isEqualTo(APP + "/dashboard");
  }

  @Test
  void redirectsToTheLoginWithTheRejectionCodeAndNoCookie() throws IOException {
    when(resolver.resolve(ArgumentMatchers.any(MicrosoftIdentity.class)))
        .thenReturn(MicrosoftLoginResult.rejected(MicrosoftUserResolver.USER_NOT_FOUND));
    MockHttpServletResponse response = new MockHttpServletResponse();

    success.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    assertThat(response.getRedirectedUrl())
        .isEqualTo(APP + "/login?error=microsoft_user_not_found");
  }

  @Test
  void failureRedirectsWithAGenericCodeWithoutLeakingTheMessage() throws IOException {
    MicrosoftLoginFailureHandler failure = new MicrosoftLoginFailureHandler(APP, audit);
    MockHttpServletResponse response = new MockHttpServletResponse();

    failure.onAuthenticationFailure(
        new MockHttpServletRequest(),
        response,
        new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "detalle interno"));

    assertThat(response.getRedirectedUrl()).isEqualTo(APP + "/login?error=microsoft_failed");
  }

  @Test
  void failureDistinguishesTheUserCancelling() throws IOException {
    MicrosoftLoginFailureHandler failure = new MicrosoftLoginFailureHandler(APP, audit);
    MockHttpServletResponse response = new MockHttpServletResponse();

    failure.onAuthenticationFailure(
        new MockHttpServletRequest(),
        response,
        new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

    assertThat(response.getRedirectedUrl()).isEqualTo(APP + "/login?error=microsoft_cancelled");
  }
}
