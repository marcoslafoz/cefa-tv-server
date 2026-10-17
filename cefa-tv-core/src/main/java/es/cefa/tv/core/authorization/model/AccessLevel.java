package es.cefa.tv.core.authorization.model;

/** Nivel de acceso concedido sobre un grupo de reproductores. */
public enum AccessLevel {
  /** Ver los reproductores del grupo y su estado. */
  VIEW,
  /** Además de ver, programar, controlar en remoto y editar. */
  OPERATE;

  /**
   * Indica si este nivel incluye el requerido.
   *
   * @param required nivel exigido
   * @return {@code true} si este nivel es igual o superior
   */
  public boolean covers(AccessLevel required) {
    return ordinal() >= required.ordinal();
  }

  /**
   * Devuelve el nivel más alto de los dos.
   *
   * @param a primer nivel
   * @param b segundo nivel
   * @return el nivel superior
   */
  public static AccessLevel max(AccessLevel a, AccessLevel b) {
    return a.covers(b) ? a : b;
  }
}
