package es.cefa.tv.core.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resumen de actividad de un intervalo.
 *
 * @param from inicio del intervalo
 * @param to fin del intervalo
 * @param total número de entradas
 * @param byOutcome entradas por resultado
 * @param topActions acciones más frecuentes
 * @param topUsers usuarios más activos
 * @param byDay entradas por día
 */
public record AuditStatsResponse(
    Instant from,
    Instant to,
    long total,
    Map<String, Long> byOutcome,
    List<ActionStat> topActions,
    List<UserStat> topUsers,
    List<DayStat> byDay) {

  /**
   * Recuento de una acción.
   *
   * @param action acción
   * @param total número de entradas
   */
  public record ActionStat(String action, long total) {}

  /**
   * Recuento de un usuario.
   *
   * @param userId identificador, o {@code null}
   * @param username nombre de usuario
   * @param total número de entradas
   */
  public record UserStat(UUID userId, String username, long total) {}

  /**
   * Recuento de un día.
   *
   * @param day día en formato {@code yyyy-MM-dd} (UTC)
   * @param total número de entradas
   */
  public record DayStat(String day, long total) {}
}
