package es.cefa.tv.core.aracast.plan;

/** Una playlist no se puede emitir en un Aracast tal como está definida. */
public class UnsupportedPlaylistException extends RuntimeException {

  /**
   * Crea la excepción.
   *
   * @param message qué playlist falla y por qué
   */
  public UnsupportedPlaylistException(String message) {
    super(message);
  }
}
