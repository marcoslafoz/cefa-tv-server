package es.cefa.tv.core.schedule.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * Regla de programación independiente de la persistencia, usada para resolver qué se emite.
 *
 * @param id identificador de la programación
 * @param playlistId playlist que emite
 * @param priority prioridad; gana la mayor
 * @param startDate primer día de vigencia o {@code null}
 * @param endDate último día de vigencia o {@code null}
 * @param startTime inicio de la franja o {@code null} si es todo el día
 * @param endTime fin de la franja (exclusivo) o {@code null} si es todo el día
 * @param daysOfWeek días de la semana, 1 lunes a 7 domingo
 * @param allDay si emite todo el día
 * @param enabled si está habilitada
 */
public record ScheduleRule(
    UUID id,
    UUID playlistId,
    int priority,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    Set<Integer> daysOfWeek,
    boolean allDay,
    boolean enabled) {

  /**
   * Crea una regla desde una entidad.
   *
   * @param schedule programación persistida
   * @return la regla equivalente
   */
  public static ScheduleRule from(Schedule schedule) {
    return new ScheduleRule(
        schedule.getId(),
        schedule.getPlaylistId(),
        schedule.getPriority(),
        schedule.getStartDate(),
        schedule.getEndDate(),
        schedule.getStartTime(),
        schedule.getEndTime(),
        Set.copyOf(java.util.Arrays.asList(schedule.getDaysOfWeek())),
        schedule.isAllDay(),
        schedule.isEnabled());
  }

  /**
   * Hora de inicio efectiva de la franja.
   *
   * @return {@code 00:00} si es todo el día
   */
  public LocalTime effectiveStart() {
    return allDay || startTime == null ? LocalTime.MIN : startTime;
  }

  /**
   * Indica si la regla está activa en un instante local.
   *
   * @param date fecha local
   * @param time hora local
   * @return {@code true} si la regla aplica
   */
  public boolean isActiveAt(LocalDate date, LocalTime time) {
    if (!enabled || !daysOfWeek.contains(date.getDayOfWeek().getValue())) {
      return false;
    }
    if (startDate != null && date.isBefore(startDate)) {
      return false;
    }
    if (endDate != null && date.isAfter(endDate)) {
      return false;
    }
    if (allDay) {
      return true;
    }
    return !time.isBefore(startTime) && time.isBefore(endTime);
  }
}
