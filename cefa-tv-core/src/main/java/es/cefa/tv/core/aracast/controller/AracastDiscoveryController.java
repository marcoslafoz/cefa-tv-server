package es.cefa.tv.core.aracast.controller;

import es.cefa.tv.core.aracast.discovery.AracastSiteDiscoveryService;
import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Búsqueda de Aracast en los rangos de una sede. */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/aracast")
@RequiredArgsConstructor
public class AracastDiscoveryController {

  private final AracastSiteDiscoveryService discovery;

  /**
   * Barre los rangos de la sede en busca de players Aracast.
   *
   * @param siteId sede
   * @return lo encontrado
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @PostMapping("/discover")
  public AracastSiteDiscoveryService.Result discover(@PathVariable UUID siteId) {
    return discovery.discover(siteId);
  }
}
