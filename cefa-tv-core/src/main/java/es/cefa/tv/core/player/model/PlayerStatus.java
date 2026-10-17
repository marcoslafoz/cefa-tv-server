package es.cefa.tv.core.player.model;

/** Estado administrativo de un reproductor. */
public enum PlayerStatus {
  /** Recibe programación y reproduce. */
  ACTIVE,
  /** Desactivado por un administrador; la API de reproductor lo rechaza. */
  DISABLED
}
