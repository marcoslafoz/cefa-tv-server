package es.cefa.tv.core.folder.dto;

import java.util.UUID;

/**
 * Referencia a una carpeta dentro de una ruta.
 *
 * @param id identificador
 * @param name nombre
 */
public record FolderRef(UUID id, String name) {}
