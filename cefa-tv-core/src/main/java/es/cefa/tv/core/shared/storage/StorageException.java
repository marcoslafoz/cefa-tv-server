package es.cefa.tv.core.shared.storage;

/** Error de acceso al almacenamiento de objetos. */
public class StorageException extends RuntimeException {

  /**
   * Crea la excepción.
   *
   * @param message descripción del fallo
   * @param cause causa original
   */
  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
