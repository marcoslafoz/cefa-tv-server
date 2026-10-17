package es.cefa.tv.core.folder.repository;

import java.util.UUID;

/** Número de elementos de una carpeta. */
public interface FolderCount {

  /**
   * Devuelve la carpeta.
   *
   * @return el identificador de la carpeta
   */
  UUID getFolderId();

  /**
   * Devuelve el recuento.
   *
   * @return el número de elementos
   */
  long getTotal();
}
