package es.cefa.tv.core.aracast.plan;

import java.util.List;

/**
 * Lo que hay que dejar en un player para emitir una programación de CEFA TV.
 *
 * @param templates plantillas necesarias, sin repetir
 * @param events eventos de la programación semanal
 */
public record AracastPlan(List<PlannedTemplate> templates, List<PlannedEvent> events) {}
