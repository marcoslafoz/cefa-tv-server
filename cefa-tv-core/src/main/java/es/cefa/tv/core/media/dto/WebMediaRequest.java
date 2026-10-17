package es.cefa.tv.core.media.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Alta de un medio de tipo web.
 *
 * @param name nombre
 * @param url dirección http o https
 * @param refreshSec segundos entre recargas, opcional
 * @param folderId carpeta de destino, o {@code null} para la raíz
 */
public record WebMediaRequest(
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "^https?://.+", message = "Debe empezar por http:// o https://")
        String url,
    @Min(0) Integer refreshSec,
    UUID folderId) {}
