package es.cefa.tv.core.aracast.ics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Franja de una programación: qué plantilla se emite, de qué hora a qué hora y qué días.
 *
 * @param template nombre de la plantilla instalada en el player
 * @param start hora de inicio
 * @param end hora de fin, posterior al inicio dentro del mismo día
 * @param days días de la semana en que se aplica; vacío significa todos los días
 * @param date día concreto de un evento que no se repite, o {@code null} si es semanal
 */
public record IcsEntry(
    @NotBlank String template,
    @NotNull LocalTime start,
    @NotNull LocalTime end,
    List<DayOfWeek> days,
    LocalDate date) {

  /** Crea una franja semanal. */
  public IcsEntry(String template, LocalTime start, LocalTime end, List<DayOfWeek> days) {
    this(template, start, end, days, null);
  }

  /** Normaliza {@code days} a una lista vacía cuando no se indica. */
  public IcsEntry {
    days = days == null ? List.of() : List.copyOf(days);
  }

  /** Indica si la franja se aplica todos los días. */
  public boolean everyDay() {
    return days.isEmpty() || days.size() == DayOfWeek.values().length;
  }

  /** Indica si la franja se aplica el día indicado. */
  public boolean appliesOn(DayOfWeek day) {
    return everyDay() || days.contains(day);
  }
}
