package es.cefa.tv.core.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.auth.security.JwtCookieAuthenticationFilter;
import es.cefa.tv.core.auth.service.JwtService;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.playerapi.security.DeviceTokenFilter;
import es.cefa.tv.core.shared.exception.ErrorResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad: sesión por cookie JWT para la administración y token de dispositivo para
 * reproductores.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Define la cadena de filtros de seguridad.
   *
   * @param http constructor de seguridad
   * @param jwtService servicio de JWT de sesión
   * @param loader cargador de usuarios con sus permisos
   * @param players repositorio de reproductores
   * @param objectMapper serializador de errores
   * @return la cadena configurada
   * @throws Exception si la configuración falla
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtService jwtService,
      AuthenticatedUserLoader loader,
      PlayerRepository players,
      ObjectMapper objectMapper)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/providers",
                        "/api/v1/public/**",
                        "/player-api/pairing/**",
                        "/ws/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/player-api/**")
                    .hasRole("PLAYER")
                    .requestMatchers("/api/v1/**")
                    .hasRole("USER")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(
                        (request, response, ex) ->
                            writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHENTICATED",
                                "Autenticación requerida"))
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN",
                                "Acceso denegado")))
        .addFilterBefore(
            new JwtCookieAuthenticationFilter(jwtService, loader),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new DeviceTokenFilter(players, objectMapper),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Codificador de contraseñas de los usuarios de administración.
   *
   * @return un codificador BCrypt
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** Escribe una respuesta de error JSON. */
  private static void writeError(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), new ErrorResponse(status, code, message, Map.of()));
  }
}
