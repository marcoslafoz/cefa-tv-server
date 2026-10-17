package es.cefa.tv.core.schedule.service;

import es.cefa.tv.core.schedule.model.ScheduleRule;
import es.cefa.tv.core.schedule.model.ScheduleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Calcula el estado y la próxima emisión de una programación con la misma regla de vigencia que
 * aplica el manifest, evaluada en la zona horaria de cada reproductor destino.
 */
public final class ScheduleTiming {

  private static final int MAX_LOOKAHEAD_DAYS = 400;
  private static final ZoneId FALLBACK_ZONE = ZoneId.of("UTC");

  /** Evita la instanciación de la clase de utilidades. */
  private ScheduleTiming() {}

  /**
   * Resultado de evaluar una programación.
   *
   * @param status estado de la programación
   * @param nextRunAt inicio de la próxima emisión posterior al instante evaluado, o {@code null} si
   *     no volverá a emitirse
   */
  public record Timing(ScheduleStatus status, Instant nextRunAt) {}

  /**
   * Evalúa la programación en las zonas horarias de sus destinos: está activa si lo está en alguna,
   * terminada si no está activa y no le quedan emisiones futuras en ninguna, y su próxima emisión
   * es la más temprana de todas las zonas.
   *
   * @param rule regla de la programación
   * @param zones zonas horarias de los reproductores destino; vacío equivale a UTC
   * @param now instante de evaluación
   * @return estado y próxima emisión
   */
  public static Timing evaluate(ScheduleRule rule, Collection<ZoneId> zones, Instant now) {
    if (!rule.enabled()) {
      return new Timing(ScheduleStatus.DISABLED, null);
    }
    Collection<ZoneId> effective = zones.isEmpty() ? List.of(FALLBACK_ZONE) : zones;
    boolean active = effective.stream().anyMatch(zone -> isActive(rule, zone, now));
    Instant next =
        effective.stream()
            .map(zone -> nextStart(rule, zone, now))
            .flatMap(Optional::stream)
            .min(Instant::compareTo)
            .orElse(null);
    ScheduleStatus status =
        active
            ? ScheduleStatus.ACTIVE_NOW
            : next == null ? ScheduleStatus.ENDED : ScheduleStatus.SCHEDULED;
    return new Timing(status, next);
  }

  /**
   * Estado de la ocurrencia de un día concreto respecto al instante actual.
   *
   * @param day día de la ocurrencia en la zona del reproductor
   * @param startTime inicio de la franja, o {@code null} si es todo el día
   * @param endTime fin de la franja, o {@code null} si es todo el día
   * @param zone zona horaria del reproductor
   * @param now instante de evaluación
   * @return {@code SCHEDULED} si aún no empezó, {@code ACTIVE_NOW} si está en curso y {@code ENDED}
   *     si ya pasó
   */
  public static ScheduleStatus occurrenceStatus(
      LocalDate day, LocalTime startTime, LocalTime endTime, ZoneId zone, Instant now) {
    ZonedDateTime start =
        ZonedDateTime.of(day, startTime == null ? LocalTime.MIN : startTime, zone);
    ZonedDateTime end =
        endTime == null
            ? ZonedDateTime.of(day.plusDays(1), LocalTime.MIN, zone)
            : ZonedDateTime.of(day, endTime, zone);
    if (now.isBefore(start.toInstant())) {
      return ScheduleStatus.SCHEDULED;
    }
    return now.isBefore(end.toInstant()) ? ScheduleStatus.ACTIVE_NOW : ScheduleStatus.ENDED;
  }

  /** Indica si la regla está dentro de su franja en la zona dada. */
  private static boolean isActive(ScheduleRule rule, ZoneId zone, Instant now) {
    ZonedDateTime local = now.atZone(zone);
    return rule.isActiveAt(local.toLocalDate(), local.toLocalTime());
  }

  /** Busca el primer inicio de franja posterior al instante dado en la zona indicada. */
  private static Optional<Instant> nextStart(ScheduleRule rule, ZoneId zone, Instant now) {
    LocalDate today = now.atZone(zone).toLocalDate();
    LocalDate first =
        rule.startDate() != null && rule.startDate().isAfter(today) ? rule.startDate() : today;
    for (int offset = 0; offset <= MAX_LOOKAHEAD_DAYS; offset++) {
      LocalDate day = first.plusDays(offset);
      if (rule.endDate() != null && day.isAfter(rule.endDate())) {
        return Optional.empty();
      }
      if (rule.isActiveAt(day, rule.effectiveStart())) {
        Instant start = ZonedDateTime.of(day, rule.effectiveStart(), zone).toInstant();
        if (start.isAfter(now)) {
          return Optional.of(start);
        }
      }
    }
    return Optional.empty();
  }
}
