package es.cefa.tv.core.media.dto;

import es.cefa.tv.core.folder.dto.FolderRef;
import es.cefa.tv.core.media.model.MediaStatus;
import es.cefa.tv.core.media.model.MediaType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Medio de la biblioteca.
 *
 * @param id identificador
 * @param name nombre
 * @param type tipo
 * @param mime tipo MIME
 * @param sizeBytes tamaño en bytes
 * @param sha256 resumen del contenido
 * @param durationMs duración en milisegundos (vídeo)
 * @param width ancho en píxeles
 * @param height alto en píxeles
 * @param pageCount número de páginas (PDF)
 * @param url dirección (web)
 * @param refreshSec segundos entre recargas (web)
 * @param status estado de procesado
 * @param progress avance de la compresión, de 0 a 100, mientras el medio está en proceso; {@code
 *     null} en caso contrario
 * @param thumbnailUrl ruta de la miniatura o {@code null}
 * @param contentUrl ruta del contenido o {@code null}
 * @param createdAt fecha de creación
 * @param folderId carpeta que lo contiene, o {@code null} si está en la raíz
 * @param folderPath ruta de la carpeta desde la raíz; vacía en la raíz y {@code null} si no se
 *     calcula (medios dentro de una playlist)
 */
public record MediaResponse(
    UUID id,
    String name,
    MediaType type,
    String mime,
    long sizeBytes,
    String sha256,
    Long durationMs,
    Integer width,
    Integer height,
    Integer pageCount,
    String url,
    Integer refreshSec,
    MediaStatus status,
    Integer progress,
    String thumbnailUrl,
    String contentUrl,
    Instant createdAt,
    UUID folderId,
    List<FolderRef> folderPath) {}
