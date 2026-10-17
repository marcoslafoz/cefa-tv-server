package es.cefa.tv.core.folder.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Renombrado o movimiento de una carpeta; los campos nulos no se tocan.
 *
 * @param name nuevo nombre
 * @param parentId nueva carpeta padre
 * @param moveToRoot mueve la carpeta a la raíz
 */
public record FolderUpdateRequest(
    @Size(max = 150) String name, UUID parentId, Boolean moveToRoot) {}
