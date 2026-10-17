package es.cefa.tv.core.folder.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Carpeta con sus contadores.
 *
 * @param id identificador
 * @param name nombre
 * @param parentId carpeta padre, o {@code null} si cuelga de la raíz
 * @param itemCount elementos que contiene directamente
 * @param subfolderCount subcarpetas directas
 * @param createdAt fecha de creación
 */
public record FolderResponse(
    UUID id, String name, UUID parentId, long itemCount, long subfolderCount, Instant createdAt) {}
