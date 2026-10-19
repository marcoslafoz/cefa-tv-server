package es.cefa.tv.core.aracast.exception;

/**
 * El player no responde: no se pudo abrir la conexión. Se distingue de otros fallos porque una
 * publicación en este caso se puede reintentar cuando el player vuelva.
 */
public class PlayerOfflineException extends AracastException {

  public PlayerOfflineException(String message, Throwable cause) {
    super(message, cause);
  }
}
