package es.cefa.tv.core.aracast.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que hay que dejar en un player para emitir una programación de CEFA TV.
 *
 * @param templates plantillas de las playlists que se emiten, sin repetir
 * @param events eventos de la programación: semanales y, para los ciclos de plantillas, con fecha
 * @param idle plantilla de la pantalla en reposo, que el player emite cuando no hay ningún evento
 * @param rolling si hay eventos con fecha, que hay que regenerar a menudo porque solo cubren las
 *     próximas horas
 */
public record AracastPlan(
    List<PlannedTemplate> templates,
    List<PlannedEvent> events,
    PlannedTemplate idle,
    boolean rolling) {

  /**
   * Todas las plantillas que deben estar en el player, la de reposo incluida.
   *
   * @return las plantillas
   */
  public List<PlannedTemplate> allTemplates() {
    List<PlannedTemplate> all = new ArrayList<>(templates);
    if (idle != null) {
      all.add(idle);
    }
    return all;
  }
}
