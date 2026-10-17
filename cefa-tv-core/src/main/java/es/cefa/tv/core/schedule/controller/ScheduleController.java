package es.cefa.tv.core.schedule.controller;

import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.schedule.dto.ScheduleRequest;
import es.cefa.tv.core.schedule.dto.ScheduleResponse;
import es.cefa.tv.core.schedule.dto.TimelineOccurrence;
import es.cefa.tv.core.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de programaciones. */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

  private final ScheduleService scheduleService;

  /**
   * Lista las programaciones.
   *
   * @return las programaciones
   */
  @RequiresPermission({Permission.SCHEDULE_MANAGE, Permission.PLAYER_VIEW})
  @GetMapping
  public List<ScheduleResponse> list() {
    return scheduleService.list();
  }

  /**
   * Devuelve las ocurrencias diarias de uno o varios reproductores para pintar el timeline. Admite
   * reproductores, grupos y sedes (parámetros repetibles o separados por comas) y solo incluye los
   * reproductores que el usuario puede ver; {@code playerId} se mantiene por compatibilidad.
   *
   * @param playerId reproductor único
   * @param playerIds reproductores
   * @param groupIds grupos de reproductores
   * @param siteIds sedes
   * @param from primer día
   * @param to último día
   * @return las ocurrencias
   */
  @RequiresPermission({Permission.SCHEDULE_MANAGE, Permission.PLAYER_VIEW})
  @GetMapping("/timeline")
  public List<TimelineOccurrence> timeline(
      @RequestParam(required = false) UUID playerId,
      @RequestParam(required = false) List<UUID> playerIds,
      @RequestParam(required = false) List<UUID> groupIds,
      @RequestParam(required = false) List<UUID> siteIds,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return scheduleService.timeline(playerId, playerIds, groupIds, siteIds, from, to);
  }

  /**
   * Crea una programación.
   *
   * @param request datos
   * @return la programación creada
   */
  @RequiresPermission(Permission.SCHEDULE_MANAGE)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ScheduleResponse create(@Valid @RequestBody ScheduleRequest request) {
    return scheduleService.create(request);
  }

  /**
   * Obtiene una programación.
   *
   * @param id identificador
   * @return la programación
   */
  @RequiresPermission({Permission.SCHEDULE_MANAGE, Permission.PLAYER_VIEW})
  @GetMapping("/{id}")
  public ScheduleResponse get(@PathVariable UUID id) {
    return scheduleService.get(id);
  }

  /**
   * Reemplaza una programación.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return la programación actualizada
   */
  @RequiresPermission(Permission.SCHEDULE_MANAGE)
  @PutMapping("/{id}")
  public ScheduleResponse replace(
      @PathVariable UUID id, @Valid @RequestBody ScheduleRequest request) {
    return scheduleService.replace(id, request);
  }

  /**
   * Borra una programación.
   *
   * @param id identificador
   */
  @RequiresPermission(Permission.SCHEDULE_MANAGE)
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    scheduleService.delete(id);
  }
}
