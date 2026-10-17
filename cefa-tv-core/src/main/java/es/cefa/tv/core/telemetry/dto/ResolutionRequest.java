package es.cefa.tv.core.telemetry.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Petición de cambio de resolución de una salida de vídeo.
 *
 * @param output nombre de la salida, por ejemplo {@code HDMI-1}
 * @param width ancho en píxeles
 * @param height alto en píxeles
 */
public record ResolutionRequest(
    @NotBlank String output, @Min(320) @Max(16384) int width, @Min(200) @Max(16384) int height) {}
