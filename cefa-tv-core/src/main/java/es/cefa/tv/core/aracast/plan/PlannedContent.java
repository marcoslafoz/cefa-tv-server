package es.cefa.tv.core.aracast.plan;

/**
 * Fichero de contenido que usa una plantilla.
 *
 * @param sha256 resumen del fichero en CEFA TV
 * @param name nombre original, con extensión
 * @param mime tipo MIME
 */
public record PlannedContent(String sha256, String name, String mime) {}
