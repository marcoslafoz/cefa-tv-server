package es.cefa.tv.core.media.model;

/**
 * Página rasterizada de un PDF.
 *
 * @param index posición de la página empezando en 0
 * @param objectKey clave del objeto en el almacenamiento
 * @param sha256 resumen del contenido
 * @param sizeBytes tamaño en bytes
 */
public record PdfPage(int index, String objectKey, String sha256, long sizeBytes) {}
