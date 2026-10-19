package es.cefa.tv.core.aracast.exception;

import es.cefa.tv.core.shared.exception.ErrorResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce los fallos de comunicación con un Aracast al formato de error de la API. */
@Slf4j
@RestControllerAdvice
public class AracastExceptionHandler {

  /**
   * El player no responde.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(PlayerOfflineException.class)
  public ResponseEntity<ErrorResponse> handleOffline(PlayerOfflineException ex) {
    return build(HttpStatus.UNPROCESSABLE_ENTITY, "ARACAST_UNREACHABLE", ex.getMessage());
  }

  /**
   * El player rechazó la contraseña.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(AracastAuthException.class)
  public ResponseEntity<ErrorResponse> handleAuth(AracastAuthException ex) {
    return build(HttpStatus.UNPROCESSABLE_ENTITY, "ARACAST_SSH_AUTH_FAILED", ex.getMessage());
  }

  /**
   * Cualquier otro fallo del player.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(AracastException.class)
  public ResponseEntity<ErrorResponse> handleAracast(AracastException ex) {
    log.warn("Fallo con un Aracast: {}", ex.getMessage());
    return build(HttpStatus.BAD_GATEWAY, "ARACAST_ERROR", ex.getMessage());
  }

  private static ResponseEntity<ErrorResponse> build(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(status.value(), code, message, Map.of()));
  }
}
