package es.cefa.tv.core.microsoft.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Resuelve la petición de autorización hacia Microsoft. Guarda en la sesión breve del flujo OAuth
 * el destino posterior al inicio de sesión (parámetros {@code redirect} y {@code locale}) ya
 * validado, y pide a Microsoft que deje elegir la cuenta.
 */
public class MicrosoftAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

  /** Atributo de sesión con la ruta de destino ya validada. */
  public static final String TARGET_ATTRIBUTE = "ctv.post-login-path";

  private final DefaultOAuth2AuthorizationRequestResolver delegate;

  /**
   * Crea el resolutor.
   *
   * @param repository registros de clientes OAuth2
   * @param authorizationBaseUri ruta base de inicio del flujo
   */
  public MicrosoftAuthorizationRequestResolver(
      ClientRegistrationRepository repository, String authorizationBaseUri) {
    this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repository, authorizationBaseUri);
    this.delegate.setAuthorizationRequestCustomizer(
        customizer ->
            customizer.additionalParameters(params -> params.put("prompt", "select_account")));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    OAuth2AuthorizationRequest resolved = delegate.resolve(request);
    remember(request, resolved);
    return resolved;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(
      HttpServletRequest request, String clientRegistrationId) {
    OAuth2AuthorizationRequest resolved = delegate.resolve(request, clientRegistrationId);
    remember(request, resolved);
    return resolved;
  }

  /** Guarda el destino validado solo cuando el flujo realmente arranca. */
  private void remember(HttpServletRequest request, OAuth2AuthorizationRequest resolved) {
    if (resolved == null) {
      return;
    }
    String target =
        SafeRedirect.targetPath(request.getParameter("redirect"), request.getParameter("locale"));
    request.getSession().setAttribute(TARGET_ATTRIBUTE, target);
  }
}
