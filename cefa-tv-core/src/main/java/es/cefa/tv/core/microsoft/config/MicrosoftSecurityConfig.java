package es.cefa.tv.core.microsoft.config;

import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.auth.security.SessionCookies;
import es.cefa.tv.core.auth.service.AuthService;
import es.cefa.tv.core.microsoft.security.MicrosoftAuthorizationRequestResolver;
import es.cefa.tv.core.microsoft.security.MicrosoftLoginFailureHandler;
import es.cefa.tv.core.microsoft.security.MicrosoftLoginSuccessHandler;
import es.cefa.tv.core.microsoft.service.MicrosoftUserResolver;
import es.cefa.tv.core.shared.config.CoreProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Inicio de sesión con Microsoft Entra ID mediante OAuth2/OpenID Connect. Solo existe cuando hay
 * credenciales reales; vive en una cadena de filtros propia, limitada a las rutas del flujo, para
 * no alterar la seguridad del resto de la API.
 */
@Configuration
@Conditional(MicrosoftEnabledCondition.class)
public class MicrosoftSecurityConfig {

  /** Identificador del registro del cliente OAuth2. */
  public static final String REGISTRATION_ID = "azure";

  private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";
  private static final String LOGIN_BASE = "https://login.microsoftonline.com/";

  /**
   * Registro del cliente OAuth2 de Microsoft con los endpoints de la versión 2.0 del directorio
   * configurado, validando el emisor del token.
   *
   * @param properties propiedades de CEFA TV
   * @return el repositorio con un único registro
   */
  @Bean
  public ClientRegistrationRepository microsoftClientRegistrationRepository(
      CoreProperties properties) {
    CoreProperties.Microsoft microsoft = properties.microsoft();
    String tenant = microsoft.tenantId().trim();
    String directory = LOGIN_BASE + tenant;
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientName("Microsoft")
            .clientId(microsoft.clientId().trim())
            .clientSecret(microsoft.clientSecret().trim())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(microsoft.effectiveRedirectUri())
            .scope("openid", "profile", "email")
            .authorizationUri(directory + "/oauth2/v2.0/authorize")
            .tokenUri(directory + "/oauth2/v2.0/token")
            .jwkSetUri(directory + "/discovery/v2.0/keys")
            .issuerUri(directory + "/v2.0")
            .userNameAttributeName("sub")
            .build();
    return new InMemoryClientRegistrationRepository(registration);
  }

  /**
   * Cadena de filtros del flujo de Microsoft, limitada a {@code /oauth2/authorization/**} y {@code
   * /login/oauth2/code/**}. La sesión HTTP solo dura lo que el flujo y se invalida al terminar.
   *
   * @param http constructor de seguridad
   * @param repository registros de clientes OAuth2
   * @param properties propiedades de CEFA TV
   * @param resolver vinculación de la identidad con el usuario local
   * @param authService servicio que emite el JWT de sesión
   * @param cookies constructor de la cookie de sesión
   * @param audit registro de auditoría de los inicios de sesión
   * @return la cadena configurada
   * @throws Exception si la configuración falla
   */
  @Bean
  @Order(1)
  public SecurityFilterChain microsoftSecurityFilterChain(
      HttpSecurity http,
      ClientRegistrationRepository repository,
      CoreProperties properties,
      MicrosoftUserResolver resolver,
      AuthService authService,
      SessionCookies cookies,
      AuditService audit)
      throws Exception {
    String appBaseUrl = properties.appBaseUrl();
    http.securityMatcher(AUTHORIZATION_BASE_URI + "/**", "/login/oauth2/code/**")
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .oauth2Login(
            login ->
                login
                    .clientRegistrationRepository(repository)
                    .authorizationEndpoint(
                        endpoint ->
                            endpoint.authorizationRequestResolver(
                                new MicrosoftAuthorizationRequestResolver(
                                    repository, AUTHORIZATION_BASE_URI)))
                    .successHandler(
                        new MicrosoftLoginSuccessHandler(
                            resolver, authService, cookies, appBaseUrl, audit))
                    .failureHandler(new MicrosoftLoginFailureHandler(appBaseUrl, audit)));
    return http.build();
  }
}
