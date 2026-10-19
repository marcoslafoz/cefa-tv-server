package es.cefa.tv.core.aracast.dto;

import java.time.Instant;

/**
 * Contenido (vídeo, imagen, PDF renderizado...) instalado en un player, según {@code
 * Template.getContent}.
 *
 * @param name nombre original del fichero
 * @param fileName nombre real en el player, con el hash incorporado
 * @param hash SHA-1 del fichero
 * @param size tamaño en bytes
 * @param modifiedAt fecha de la última subida
 */
public record PlayerContent(
    String name, String fileName, String hash, long size, Instant modifiedAt) {}
