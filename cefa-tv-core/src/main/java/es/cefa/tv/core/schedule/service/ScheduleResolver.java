package es.cefa.tv.core.schedule.service;

import es.cefa.tv.core.schedule.model.ScheduleRule;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Decide qué programación gana en un instante dado, con la misma regla que aplica el reproductor.
 */
public final class ScheduleResolver {

  /** Evita la instanciación de la clase de utilidades. */
  private ScheduleResolver() {}

  /**
   * Resuelve la programación activa: mayor prioridad y, en empate, la que empezó más tarde.
   *
   * @param rules reglas candidatas
   * @param instant instante en la zona horaria del reproductor
   * @return la regla ganadora, si hay alguna activa
   */
  public static Optional<ScheduleRule> resolve(
      Collection<ScheduleRule> rules, ZonedDateTime instant) {
    return rules.stream()
        .filter(rule -> rule.isActiveAt(instant.toLocalDate(), instant.toLocalTime()))
        .max(
            Comparator.comparingInt(ScheduleRule::priority)
                .thenComparing(ScheduleRule::effectiveStart)
                .thenComparing(ScheduleRule::id));
  }
}
