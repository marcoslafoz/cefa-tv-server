package es.cefa.tv.core.microsoft.security;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.audit.service.AuditEvent;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.auth.security.SessionCookies;
import es.cefa.tv.core.auth.service.AuthService;
import es.cefa.tv.core.microsoft.dto.MicrosoftIdentity;
import es.cefa.tv.core.microsoft.dto.MicrosoftLoginResult;
import es.cefa.tv.core.microsoft.service.MicrosoftUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Cierra el flujo de Microsoft: resuelve el usuario local, emite la misma cookie de sesión {@code
 * ctv_session} del inicio de sesión normal y redirige a la interfaz, o al login con un código de
 * error si se rechaza.
 */
@RequiredArgsConstructor
public class MicrosoftLoginSuccessHandler implements AuthenticationSuccessHandler {

  private final MicrosoftUserResolver resolver;
  private final AuthService authService;
  private final SessionCookies cookies;
  private final String appBaseUrl;
  private final AuditService audit;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
    MicrosoftIdentity identity = MicrosoftIdentity.from(oidcUser);
    MicrosoftLoginResult result = resolver.resolve(identity);
    recordLogin(request, identity, result);
    HttpSession session = request.getSession(false);
    String path = pathFrom(session);
    if (session != null) {
      session.invalidate();
    }
    if (!result.isAccepted()) {
      response.sendRedirect(
          SafeRedirect.absolute(appBaseUrl, "/login?error=" + result.errorCode()));
      return;
    }
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookies.create(authService.issueToken(result.user())).toString());
    response.sendRedirect(SafeRedirect.absolute(appBaseUrl, path));
  }

  /** Deja constancia del inicio de sesión aceptado o del motivo del rechazo. */
  private void recordLogin(
      HttpServletRequest request, MicrosoftIdentity identity, MicrosoftLoginResult result) {
    AuditClient client = AuditClient.of(request);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("email", identity.email());
    if (result.isAccepted()) {
      audit.record(
          new AuditEvent(
              result.user().getId(),
              result.user().getUsername(),
              "AUTH_LOGIN_MICROSOFT",
              "USER",
              result.user().getId().toString(),
              result.user().getUsername(),
              AuditOutcome.SUCCESS,
              details,
              client.ip(),
              client.userAgent()));
      return;
    }
    details.put("errorCode", result.errorCode());
    audit.record(
        new AuditEvent(
            null,
            identity.email(),
            "AUTH_LOGIN_MICROSOFT",
            "USER",
            null,
            identity.email(),
            AuditOutcome.DENIED,
            details,
            client.ip(),
            client.userAgent()));
  }

  /** Lee el destino guardado al iniciar el flujo, o el panel por defecto. */
  private static String pathFrom(HttpSession session) {
    if (session == null) {
      return "/dashboard";
    }
    Object stored = session.getAttribute(MicrosoftAuthorizationRequestResolver.TARGET_ATTRIBUTE);
    return stored instanceof String value
        ? SafeRedirect.sanitizePath(value).orElse("/dashboard")
        : "/dashboard";
  }
}
