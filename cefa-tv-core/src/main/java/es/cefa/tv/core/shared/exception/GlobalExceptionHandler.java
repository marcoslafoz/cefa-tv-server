package es.cefa.tv.core.shared.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Traduce las excepciones de la aplicación al formato {@link ErrorResponse}. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Gestiona las excepciones de negocio.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
    return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), ex.getDetails());
  }

  /**
   * Gestiona los errores de validación del cuerpo.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> details = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Datos no válidos", details);
  }

  /**
   * Gestiona las violaciones de restricciones de parámetros.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
    Map<String, String> details = new LinkedHashMap<>();
    ex.getConstraintViolations()
        .forEach(v -> details.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Datos no válidos", details);
  }

  /**
   * Gestiona los cuerpos ilegibles.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    return build(
        HttpStatus.BAD_REQUEST, "MALFORMED_BODY", "Cuerpo de la petición no válido", Map.of());
  }

  /**
   * Gestiona las subidas que superan el límite.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException ex) {
    return build(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "UPLOAD_TOO_LARGE",
        "El fichero es demasiado grande",
        Map.of());
  }

  /**
   * Gestiona los accesos denegados.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException ex) {
    return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acceso denegado", Map.of());
  }

  /**
   * Gestiona las rutas inexistentes.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso no encontrado", Map.of());
  }

  /**
   * Gestiona los métodos HTTP no admitidos.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException ex) {
    return build(
        HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Método no admitido", Map.of());
  }

  /**
   * Gestiona los parámetros ausentes o con un tipo incorrecto.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex) {
    return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parámetro no válido", Map.of());
  }

  /**
   * Gestiona cualquier otra excepción como un error interno.
   *
   * @param ex la excepción
   * @return la respuesta de error
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("Error no controlado", ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor", Map.of());
  }

  /** Construye la respuesta de error con el estado indicado. */
  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String errorCode, String message, Map<String, String> details) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(status.value(), errorCode, message, details));
  }
}
