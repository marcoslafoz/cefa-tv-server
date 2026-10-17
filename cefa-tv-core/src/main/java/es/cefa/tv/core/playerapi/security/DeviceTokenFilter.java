package es.cefa.tv.core.playerapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerStatus;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.exception.ErrorResponse;
import es.cefa.tv.core.shared.util.Hashes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Autentica las llamadas de {@code /player-api} con el token de dispositivo. */
@RequiredArgsConstructor
public class DeviceTokenFilter extends OncePerRequestFilter {

  private static final String PAIRING_PATH = "/player-api/pairing/";
  private static final String BEARER = "Bearer ";

  private final PlayerRepository players;
  private final ObjectMapper objectMapper;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/player-api/") || path.startsWith(PAIRING_PATH);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<Player> player =
        extractToken(request).map(Hashes::sha256Hex).flatMap(players::findByDeviceTokenHash);
    if (player.isPresent() && player.get().getStatus() == PlayerStatus.DISABLED) {
      writeError(
          response, HttpServletResponse.SC_FORBIDDEN, "PLAYER_DISABLED", "Reproductor desactivado");
      return;
    }
    player.ifPresent(
        found ->
            SecurityContextHolder.getContext()
                .setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        new AuthenticatedPlayer(found.getId()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_PLAYER")))));
    chain.doFilter(request, response);
  }

  /** Extrae el token de la cabecera {@code Authorization: Bearer}. */
  private Optional<String> extractToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER) && header.length() > BEARER.length()) {
      return Optional.of(header.substring(BEARER.length()).trim());
    }
    return Optional.empty();
  }

  /** Escribe una respuesta de error JSON. */
  private void writeError(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), new ErrorResponse(status, code, message, Map.of()));
  }
}
