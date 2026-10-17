package es.cefa.tv.core.realtime;

import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerStatus;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.util.Hashes;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Autentica el handshake de {@code /ws/player} con el token de dispositivo de la query. */
@Component
@RequiredArgsConstructor
public class PlayerHandshakeInterceptor implements HandshakeInterceptor {

  private final PlayerRepository players;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return false;
    }
    String token = servletRequest.getServletRequest().getParameter("token");
    Optional<Player> player =
        token == null || token.isBlank()
            ? Optional.empty()
            : players.findByDeviceTokenHash(Hashes.sha256Hex(token));
    if (player.isEmpty() || player.get().getStatus() != PlayerStatus.ACTIVE) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    attributes.put(PlayerSocketHandler.PLAYER_ID, player.get().getId());
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {}
}
