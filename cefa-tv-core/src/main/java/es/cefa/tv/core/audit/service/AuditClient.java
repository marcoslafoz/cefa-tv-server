package es.cefa.tv.core.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.socket.WebSocketSession;

/**
 * Origen de una acción auditada.
 *
 * @param ip dirección de origen
 * @param userAgent navegador o cliente
 */
public record AuditClient(String ip, String userAgent) {

  private static final String FORWARDED_FOR = "X-Forwarded-For";
  private static final String USER_AGENT = "User-Agent";

  /** Origen desconocido. */
  public static final AuditClient UNKNOWN = new AuditClient(null, null);

  /**
   * Obtiene el origen de una petición HTTP; detrás de nginx usa {@code X-Forwarded-For}.
   *
   * @param request petición
   * @return el origen
   */
  public static AuditClient of(HttpServletRequest request) {
    String forwarded = request.getHeader(FORWARDED_FOR);
    String ip =
        forwarded != null && !forwarded.isBlank()
            ? forwarded.split(",")[0].trim()
            : request.getRemoteAddr();
    return new AuditClient(ip, request.getHeader(USER_AGENT));
  }

  /**
   * Obtiene el origen de la petición HTTP en curso.
   *
   * @return el origen, o desconocido si no hay petición asociada al hilo
   */
  public static AuditClient current() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet
        ? of(servlet.getRequest())
        : UNKNOWN;
  }

  /**
   * Obtiene el origen de una conexión WebSocket.
   *
   * @param session sesión
   * @return el origen
   */
  public static AuditClient of(WebSocketSession session) {
    String forwarded = session.getHandshakeHeaders().getFirst(FORWARDED_FOR);
    String ip =
        forwarded != null && !forwarded.isBlank()
            ? forwarded.split(",")[0].trim()
            : session.getRemoteAddress() == null
                ? null
                : session.getRemoteAddress().getAddress().getHostAddress();
    return new AuditClient(ip, session.getHandshakeHeaders().getFirst(USER_AGENT));
  }
}
