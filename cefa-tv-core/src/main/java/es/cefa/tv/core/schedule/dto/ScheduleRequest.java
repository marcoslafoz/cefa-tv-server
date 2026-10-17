package es.cefa.tv.core.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Creación o reemplazo de una programación.
 *
 * @param name nombre
 * @param playlistId playlist a emitir; obligatorio salvo que se indique {@code assetId}
 * @param assetId medio suelto a emitir; crea una playlist oculta con ese medio
 * @param durationSec segundos del medio suelto, 10 por defecto
 * @param priority prioridad, 10 por defecto
 * @param startDate primer día de vigencia
 * @param endDate último día de vigencia
 * @param startTime inicio de la franja
 * @param endTime fin de la franja
 * @param daysOfWeek días de la semana 1 a 7, todos por defecto
 * @param allDay si emite todo el día
 * @param enabled si está habilitada, sí por defecto
 * @param targets destinos
 */
public record ScheduleRequest(
    @NotBlank String name,
    UUID playlistId,
    UUID assetId,
    @Min(1) Integer durationSec,
    Integer priority,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    List<Integer> daysOfWeek,
    boolean allDay,
    Boolean enabled,
    @NotNull @Valid Targets targets) {

  /**
   * Destinos de la programación.
   *
   * @param playerIds reproductores
   * @param groupIds grupos
   * @param siteIds sedes: la programación llega a todos los reproductores de cada sede
   */
  public record Targets(List<UUID> playerIds, List<UUID> groupIds, List<UUID> siteIds) {}
}
