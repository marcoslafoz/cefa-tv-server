package es.cefa.tv.core.aracast.plan;

import java.util.List;

/**
 * Plantilla que hay que tener instalada en el player.
 *
 * @param name nombre en el player, con la marca {@code [ctv-...]} que permite reconocerla
 * @param kind tipo
 * @param contents ficheros que usa, en orden
 * @param secondsPerSlide segundos por imagen en un pase de diapositivas
 * @param url dirección, en las plantillas web
 * @param muted si los vídeos van sin sonido
 */
public record PlannedTemplate(
    String name,
    TemplateKind kind,
    List<PlannedContent> contents,
    int secondsPerSlide,
    String url,
    boolean muted) {}
