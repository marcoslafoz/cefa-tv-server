package es.cefa.tv.core.media.model;

/** Estado de procesado de un medio. */
public enum MediaStatus {
  /** Listo para usarse. */
  READY,
  /** En procesado. */
  PROCESSING,
  /** El procesado falló. */
  FAILED
}
