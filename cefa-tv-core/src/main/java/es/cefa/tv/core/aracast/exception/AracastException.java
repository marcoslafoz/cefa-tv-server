package es.cefa.tv.core.aracast.exception;

/** Error al comunicar con un player Aracast o respuesta de fallo del propio player. */
public class AracastException extends RuntimeException {

  public AracastException(String message) {
    super(message);
  }

  public AracastException(String message, Throwable cause) {
    super(message, cause);
  }
}
