package es.cefa.tv.core.aracast.dto;

import java.time.Instant;
import java.util.List;

/**
 * Plantilla instalada en un player, según {@code Template.getTemplates}.
 *
 * @param name nombre de la plantilla
 * @param fileName nombre del zip con el que se subió
 * @param hash SHA-1 del zip
 * @param size tamaño del zip en bytes
 * @param modifiedAt fecha de la última subida
 * @param elements descripción de los elementos que contiene, tal y como la da el player
 * @param thumbnail miniatura en formato data URI, si el player la genera
 */
public record PlayerTemplate(
    String name,
    String fileName,
    String hash,
    long size,
    Instant modifiedAt,
    List<String> elements,
    String thumbnail) {}
