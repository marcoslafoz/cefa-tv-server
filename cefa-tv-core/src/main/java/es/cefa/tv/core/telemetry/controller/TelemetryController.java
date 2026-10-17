package es.cefa.tv.core.telemetry.controller;

import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.annotation.RequiresScope;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.telemetry.dto.MetricPoint;
import es.cefa.tv.core.telemetry.dto.ResolutionRequest;
import es.cefa.tv.core.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de telemetría y de gestión de pantalla de un reproductor. */
@RestController
@RequestMapping("/api/v1/players/{id}")
@RequiredArgsConstructor
public class TelemetryController {

  private final TelemetryService telemetryService;

  /**
   * Devuelve la serie de telemetría.
   *
   * @param id reproductor
   * @param hours horas hacia atrás (1 a 24)
   * @return las muestras
   */
  @RequiresPermission(Permission.PLAYER_VIEW)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.VIEW)
  @GetMapping("/metrics")
  public List<MetricPoint> metrics(
      @PathVariable UUID id, @RequestParam(defaultValue = "1") int hours) {
    return telemetryService.series(id, hours);
  }

  /**
   * Pide volver a detectar las salidas de vídeo.
   *
   * @param id reproductor
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @PostMapping("/display/redetect")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void redetect(@PathVariable UUID id) {
    telemetryService.redetectDisplays(id);
  }

  /**
   * Pide cambiar la resolución de una salida.
   *
   * @param id reproductor
   * @param request salida y resolución
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @PostMapping("/display/resolution")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void resolution(@PathVariable UUID id, @Valid @RequestBody ResolutionRequest request) {
    telemetryService.setResolution(id, request);
  }
}
