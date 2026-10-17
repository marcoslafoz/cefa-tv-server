package es.cefa.tv.core.playlist.dto;

import es.cefa.tv.core.playlist.model.Fit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Elemento de una playlist en una petición.
 *
 * @param assetId medio
 * @param durationSec segundos de reproducción (por página en un PDF)
 * @param fit ajuste a pantalla, {@code COVER} por defecto
 * @param muted si el vídeo se silencia, por defecto sí
 */
public record PlaylistItemRequest(
    @NotNull UUID assetId, @Min(1) int durationSec, Fit fit, Boolean muted) {}
