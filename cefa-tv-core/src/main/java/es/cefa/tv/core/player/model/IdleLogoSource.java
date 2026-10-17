package es.cefa.tv.core.player.model;

/** Origen del logo efectivo de un reproductor. */
public enum IdleLogoSource {
  /** Lo ha elegido el propio reproductor. */
  PLAYER,
  /** Lo hereda de su grupo. */
  GROUP,
  /** Ni el reproductor ni su grupo lo definen: se usa el logo por defecto. */
  DEFAULT
}
