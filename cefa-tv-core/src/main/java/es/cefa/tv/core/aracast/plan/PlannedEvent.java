package es.cefa.tv.core.aracast.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Cambio a una plantilla en una franja horaria.
 *
 * @param template nombre de la plantilla en el player
 * @param start hora de inicio
 * @param end hora de fin
 * @param days días de la semana en que se repite cada semana; vacío si es un evento de un día
 * @param date día concreto de un evento que no se repite, o {@code null} si es semanal
 */
public record PlannedEvent(
    String template, LocalTime start, LocalTime end, Set<DayOfWeek> days, LocalDate date) {}
