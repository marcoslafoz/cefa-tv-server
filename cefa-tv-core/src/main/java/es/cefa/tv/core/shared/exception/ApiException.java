package es.cefa.tv.core.shared.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Excepción de negocio que se traduce a un {@link ErrorResponse} con el estado HTTP indicado. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String errorCode;
  private final Map<String, String> details;

  /**
   * Crea la excepción.
   *
   * @param status estado HTTP
   * @param errorCode código de error estable
   * @param message mensaje legible
   * @param details motivos por campo
   */
  public ApiException(
      HttpStatus status, String errorCode, String message, Map<String, String> details) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
    this.details = details;
  }

  /**
   * Crea un 404 para una entidad.
   *
   * @param entity nombre de la entidad
   * @return la excepción
   */
  public static ApiException notFound(String entity) {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " no encontrado", Map.of());
  }

  /**
   * Crea un 400.
   *
   * @param errorCode código de error
   * @param message mensaje legible
   * @return la excepción
   */
  public static ApiException badRequest(String errorCode, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, errorCode, message, Map.of());
  }

  /**
   * Crea un 409.
   *
   * @param errorCode código de error
   * @param message mensaje legible
   * @return la excepción
   */
  public static ApiException conflict(String errorCode, String message) {
    return new ApiException(HttpStatus.CONFLICT, errorCode, message, Map.of());
  }

  /**
   * Crea un 401.
   *
   * @param errorCode código de error
   * @param message mensaje legible
   * @return la excepción
   */
  public static ApiException unauthorized(String errorCode, String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, errorCode, message, Map.of());
  }

  /**
   * Crea un 403.
   *
   * @param errorCode código de error
   * @param message mensaje legible
   * @return la excepción
   */
  public static ApiException forbidden(String errorCode, String message) {
    return new ApiException(HttpStatus.FORBIDDEN, errorCode, message, Map.of());
  }

  /**
   * Crea un 429.
   *
   * @param errorCode código de error
   * @param message mensaje legible
   * @return la excepción
   */
  public static ApiException tooManyRequests(String errorCode, String message) {
    return new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode, message, Map.of());
  }

  /**
   * Crea un 422.
   *
   * @param errorCode código de error
   * @param message mensaje legible
   * @return la excepción
   */
  public static ApiException unprocessable(String errorCode, String message) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message, Map.of());
  }

  /**
   * Devuelve el estado HTTP.
   *
   * @return el estado
   */
  public HttpStatus getStatus() {
    return status;
  }

  /**
   * Devuelve el código de error.
   *
   * @return el código
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Devuelve los motivos por campo.
   *
   * @return los detalles
   */
  public Map<String, String> getDetails() {
    return details;
  }
}
