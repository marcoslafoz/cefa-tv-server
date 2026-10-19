package es.cefa.tv.core.media.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Compresión automática de las fotos y los vídeos que se suben a la biblioteca.
 *
 * @param enabled si se comprime al subir
 * @param maxWidth ancho máximo en píxeles; lo mayor se reduce y lo menor no se toca
 * @param maxHeight alto máximo en píxeles
 * @param imageQuality calidad JPEG de {@code ffmpeg} ({@code -q:v}, de 2 a 31; 4 equivale a calidad
 *     85)
 * @param videoCrf factor de calidad constante de H.264 (menor es más calidad; 23 es equilibrado)
 * @param videoPreset velocidad de codificación de {@code x264}
 * @param audioBitrate tasa de bits del audio AAC
 * @param videoTimeoutMinutes tiempo máximo para comprimir un vídeo
 * @param minSavingPercent ahorro mínimo, en porcentaje, para quedarse con la versión comprimida de
 *     un fichero que no hace falta reducir de tamaño
 */
@ConfigurationProperties(prefix = "cefa.tv.media.compression")
public record MediaCompressionProperties(
    boolean enabled,
    int maxWidth,
    int maxHeight,
    int imageQuality,
    int videoCrf,
    String videoPreset,
    String audioBitrate,
    int videoTimeoutMinutes,
    int minSavingPercent) {}
