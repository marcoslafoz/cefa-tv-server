package es.cefa.tv.core.realtime;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.auth.security.SessionCookies;
import es.cefa.tv.core.auth.service.JwtService;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessPolicy;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Autentica el handshake de {@code /ws/remote/{playerId}} con la cookie de sesión y exige el
 * permiso de control remoto con acceso de operación sobre el grupo del reproductor.
 */
@Component
@RequiredArgsConstructor
public class RemoteHandshakeInterceptor implements HandshakeInterceptor {

  private static final String PATH_PREFIX = "/ws/remote/";

  private final JwtService jwtService;
  private final AuthenticatedUserLoader loader;
  private final PlayerRepository players;
  private final AuditService audit;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return false;
    }
    Optional<AuthenticatedUser> user =
        SessionCookies.read(servletRequest.getServletRequest())
            .flatMap(jwtService::parse)
            .flatMap(loader::load);
    if (user.isEmpty()) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    String path = request.getURI().getPath();
    try {
      UUID playerId =
          UUID.fromString(path.substring(path.indexOf(PATH_PREFIX) + PATH_PREFIX.length()));
      Optional<Player> player = players.findById(playerId);
      if (player.isEmpty()) {
        response.setStatusCode(HttpStatus.NOT_FOUND);
        return false;
      }
      if (!AccessPolicy.canActOnPlayer(
          user.get(),
          Permission.PLAYER_REMOTE,
          player.get().getGroupId(),
          player.get().getSiteId(),
          AccessLevel.OPERATE)) {
        audit.recordUser(
            user.get(),
            AuditClient.of(servletRequest.getServletRequest()),
            "PLAYER_REMOTE_START",
            "PLAYER",
            playerId.toString(),
            player.get().getName(),
            AuditOutcome.DENIED,
            java.util.Map.of("reason", "FORBIDDEN"));
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }
      attributes.put(RemoteViewerHandler.PLAYER_ID, playerId);
      attributes.put(RemoteViewerHandler.USER, user.get());
      return true;
    } catch (IllegalArgumentException e) {
      response.setStatusCode(HttpStatus.BAD_REQUEST);
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {}
}
