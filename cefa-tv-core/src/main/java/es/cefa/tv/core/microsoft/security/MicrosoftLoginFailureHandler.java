package es.cefa.tv.core.microsoft.security;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.audit.service.AuditEvent;
import es.cefa.tv.core.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Atiende los fallos del flujo de Microsoft redirigiendo al login con un código de error genérico,
 * sin reenviar a la interfaz el texto de la excepción.
 */
@Slf4j
@RequiredArgsConstructor
public class MicrosoftLoginFailureHandler implements AuthenticationFailureHandler {

  private final String appBaseUrl;
  private final AuditService audit;

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    String code = errorCode(exception);
    log.warn("Fallo en el inicio de sesión con Microsoft ({}): {}", code, exception.getMessage());
    AuditClient client = AuditClient.of(request);
    audit.record(
        new AuditEvent(
            null,
            null,
            "AUTH_LOGIN_MICROSOFT",
            "USER",
            null,
            null,
            AuditOutcome.DENIED,
            Map.of("errorCode", code),
            client.ip(),
            client.userAgent()));
    response.sendRedirect(SafeRedirect.absolute(appBaseUrl, "/login?error=" + code));
  }

  /** Distingue la cancelación del usuario de un fallo técnico. */
  private static String errorCode(AuthenticationException exception) {
    if (exception instanceof OAuth2AuthenticationException oauth
        && "access_denied".equals(oauth.getError().getErrorCode())) {
      return "microsoft_cancelled";
    }
    return "microsoft_failed";
  }
}
