package es.cefa.tv.core.auth.security;

import es.cefa.tv.core.auth.service.JwtService;
import es.cefa.tv.core.shared.config.CoreProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Construye y lee la cookie de sesión {@code ctv_session}. */
@Component
@RequiredArgsConstructor
public class SessionCookies {

  /** Nombre de la cookie de sesión. */
  public static final String NAME = "ctv_session";

  private final CoreProperties properties;
  private final JwtService jwtService;

  /**
   * Crea la cookie que contiene el token.
   *
   * @param token JWT de sesión
   * @return la cookie lista para enviar
   */
  public ResponseCookie create(String token) {
    return base(token, jwtService.getTtl());
  }

  /**
   * Crea la cookie que invalida la sesión.
   *
   * @return la cookie de borrado
   */
  public ResponseCookie clear() {
    return base("", Duration.ZERO);
  }

  /**
   * Extrae el valor de la cookie de sesión de una petición.
   *
   * @param request petición HTTP
   * @return el token si existe
   */
  public static Optional<String> read(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
        return Optional.of(cookie.getValue());
      }
    }
    return Optional.empty();
  }

  /** Construye la cookie de sesión con los atributos de seguridad comunes. */
  private ResponseCookie base(String value, Duration maxAge) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(properties.jwt().cookieSecure())
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build();
  }
}
