package es.cefa.tv.core.aracast.controller;

import es.cefa.tv.core.aracast.management.AracastClockService;
import es.cefa.tv.core.aracast.management.AracastNetworkService;
import es.cefa.tv.core.aracast.management.ClockResult;
import es.cefa.tv.core.aracast.management.ClockSyncRequest;
import es.cefa.tv.core.aracast.management.NetworkChangeRequest;
import es.cefa.tv.core.aracast.management.NetworkChangeResult;
import es.cefa.tv.core.aracast.management.NetworkInfo;
import es.cefa.tv.core.aracast.service.AracastCommandService;
import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.annotation.RequiresScope;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.shared.exception.ApiException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Hora y red de un reproductor Aracast. */
@RestController
@RequestMapping("/api/v1/players/{id}/aracast")
@RequiredArgsConstructor
public class AracastManagementController {

  private final AracastCommandService aracast;
  private final AracastClockService clock;
  private final AracastNetworkService network;
  private final AccessService access;

  /**
   * Hora del player.
   *
   * @param id reproductor
   * @return hora, zona y desfase
   */
  @RequiresPermission(Permission.PLAYER_VIEW)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.VIEW)
  @GetMapping("/clock")
  public ClockResult clock(@PathVariable UUID id) {
    return clock.read(aracast.hostOf(id));
  }

  /**
   * Sincroniza la hora del player con la del servidor o con un servidor NTP.
   *
   * @param id reproductor
   * @param request origen de la hora
   * @return la hora resultante
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @PostMapping("/clock")
  public ClockResult syncClock(
      @PathVariable UUID id, @Valid @RequestBody ClockSyncRequest request) {
    String host = aracast.hostOf(id);
    try {
      return request.mode() == ClockSyncRequest.Mode.SERVER
          ? clock.syncFromServer(host)
          : clock.syncNtp(host, request.server());
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_CLOCK_SYNC", e.getMessage());
    }
  }

  /**
   * Red del player.
   *
   * @param id reproductor
   * @return configuración guardada y activa
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @GetMapping("/network")
  public NetworkInfo network(@PathVariable UUID id) {
    return network.get(aracast.hostOf(id));
  }

  /**
   * Cambia la red y el nombre del player; solo administradores.
   *
   * @param id reproductor
   * @param request configuración completa nueva
   * @return qué se hizo
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @PutMapping("/network")
  public NetworkChangeResult changeNetwork(
      @PathVariable UUID id, @Valid @RequestBody NetworkChangeRequest request) {
    access.requireAdmin();
    return network.apply(aracast.hostOf(id), request);
  }
}
