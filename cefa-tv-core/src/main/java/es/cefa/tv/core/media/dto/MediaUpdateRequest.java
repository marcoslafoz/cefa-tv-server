package es.cefa.tv.core.media.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Modificación de un medio; los campos nulos no se tocan.
 *
 * @param name nuevo nombre
 * @param durationMs duración por defecto en milisegundos
 * @param url nueva dirección (solo web)
 * @param refreshSec segundos entre recargas (solo web)
 * @param folderId carpeta de destino
 * @param moveToRoot saca el medio de su carpeta y lo deja en la raíz
 */
public record MediaUpdateRequest(
    String name,
    @Min(0) Long durationMs,
    @Pattern(regexp = "^https?://.+", message = "Debe empezar por http:// o https://") String url,
    @Min(0) Integer refreshSec,
    UUID folderId,
    Boolean moveToRoot) {}
