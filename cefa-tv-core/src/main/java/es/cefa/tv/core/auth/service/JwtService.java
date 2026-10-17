package es.cefa.tv.core.auth.service;

import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.shared.config.CoreProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Emisión y validación de los JWT de sesión, que solo identifican al usuario. */
@Service
public class JwtService {

  private final SecretKey key;
  private final Duration ttl;

  /**
   * Crea el servicio.
   *
   * @param properties propiedades de la aplicación
   */
  public JwtService(CoreProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    this.ttl = Duration.ofHours(properties.jwt().ttlHours());
  }

  /**
   * Genera el token de sesión de un usuario.
   *
   * @param user usuario autenticado
   * @return el JWT firmado
   */
  public String issue(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getId().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  /**
   * Valida un token y devuelve el usuario al que pertenece.
   *
   * @param token JWT recibido
   * @return el identificador del usuario si el token es válido
   */
  public Optional<UUID> parse(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      return Optional.of(UUID.fromString(claims.getSubject()));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Devuelve la duración de la sesión.
   *
   * @return la duración
   */
  public Duration getTtl() {
    return ttl;
  }
}
