package es.cefa.tv.core.player.model;

/** Tipo de dispositivo que reproduce la programación. */
public enum PlayerKind {
  /** Reproductor de CEFA TV, que descarga su programación del servidor. */
  CEFA_TV,
  /** Player Aracast, al que el servidor empuja plantillas y programación por red. */
  ARACAST
}
