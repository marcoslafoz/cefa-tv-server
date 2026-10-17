package es.cefa.tv.core.media.model;

/** Tipo de medio de la biblioteca. */
public enum MediaType {
  /** Imagen estática. */
  IMAGE,
  /** Vídeo. */
  VIDEO,
  /** Documento PDF, que se reproduce como una imagen por página. */
  PDF,
  /** Página web cargada desde una URL. */
  WEB,
  /** Fichero HTML autocontenido. */
  HTML
}
