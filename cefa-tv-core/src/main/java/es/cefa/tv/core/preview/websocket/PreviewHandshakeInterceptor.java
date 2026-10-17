package es.cefa.tv.core.preview.websocket;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.auth.security.SessionCookies;
import es.cefa.tv.core.auth.service.JwtService;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessPolicy;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
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

/**
 * Autentica el handshake de {@code /ws/previews} con la cookie de sesión y exige el permiso de ver
 * reproductores.
 */
@Component
@RequiredArgsConstructor
public class PreviewHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtService jwtService;
  private final AuthenticatedUserLoader loader;

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
    if (!AccessPolicy.hasPermission(user.get(), Permission.PLAYER_VIEW)) {
      response.setStatusCode(HttpStatus.FORBIDDEN);
      return false;
    }
    attributes.put(PreviewViewerHandler.USER, user.get());
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {}
}
