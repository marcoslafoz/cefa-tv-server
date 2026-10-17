package es.cefa.tv.core.player.model;

/** Orden que el servidor envía a un reproductor. */
public enum CommandType {
  /** Recarga la vista de reproducción. */
  RELOAD,
  /** Reinicia el reproductor. */
  RESTART,
  /** Da de baja el reproductor y borra su estado local. */
  UNENROLL,
  /** Vuelve a detectar las salidas de vídeo y a elegir la primera conectada. */
  DISPLAY_REDETECT,
  /**
   * Cambia la resolución de una salida de vídeo; lleva {@code output}, {@code width} y {@code
   * height}.
   */
  SET_RESOLUTION
}
