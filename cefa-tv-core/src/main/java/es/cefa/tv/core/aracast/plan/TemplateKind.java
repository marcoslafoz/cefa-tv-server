package es.cefa.tv.core.aracast.plan;

/** Tipo de plantilla de Aracast que se genera. */
public enum TemplateKind {
  /** Una sola imagen. */
  IMAGE,
  /** Varias imágenes con la misma duración. */
  SLIDESHOW,
  /** Uno o varios vídeos en bucle. */
  VIDEOS,
  /** Una página web. */
  WEB,
  /** Un fichero HTML servido por el nginx local del player. */
  HTML
}
