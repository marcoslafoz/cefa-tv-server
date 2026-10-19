package es.cefa.tv.core.aracast.exception;

/** El player rechazó la contraseña SSH indicada. */
public class AracastAuthException extends AracastException {

  /**
   * Crea la excepción.
   *
   * @param message detalle del rechazo
   * @param cause causa original
   */
  public AracastAuthException(String message, Throwable cause) {
    super(message, cause);
  }
}
