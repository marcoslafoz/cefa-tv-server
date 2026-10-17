package es.cefa.tv.core.folder.service;

import es.cefa.tv.core.shared.exception.ApiException;
import java.util.UUID;

/**
 * Ámbito de un listado dentro del árbol de carpetas.
 *
 * @param root si el ámbito es la raíz
 * @param folderId carpeta del ámbito cuando no es la raíz
 * @param recursive si incluye también todo lo que cuelga de ella
 */
public record FolderScope(boolean root, UUID folderId, boolean recursive) {

  private static final String ROOT = "root";

  /**
   * Interpreta el parámetro {@code folderId} de un listado.
   *
   * @param raw {@code root}, un identificador de carpeta o {@code null} para no filtrar
   * @param recursive si se incluyen las subcarpetas
   * @return el ámbito, o {@code null} si no hay filtro por carpeta
   */
  public static FolderScope parse(String raw, boolean recursive) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    if (ROOT.equalsIgnoreCase(raw.trim())) {
      return new FolderScope(true, null, recursive);
    }
    try {
      return new FolderScope(false, UUID.fromString(raw.trim()), recursive);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_ID", "Identificador de carpeta no válido");
    }
  }
}
