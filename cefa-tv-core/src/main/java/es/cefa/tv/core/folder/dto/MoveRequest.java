package es.cefa.tv.core.folder.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Movimiento masivo de medios o playlists a una carpeta.
 *
 * @param ids elementos a mover
 * @param folderId carpeta de destino, o {@code null} para la raíz
 */
public record MoveRequest(@NotEmpty @Size(max = 500) List<UUID> ids, UUID folderId) {}
