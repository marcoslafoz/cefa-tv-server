package es.cefa.tv.core.player.controller;

import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.annotation.RequiresScope;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.delivery.dto.DeliveryResponse;
import es.cefa.tv.core.player.dto.PlayerResponse;
import es.cefa.tv.core.player.dto.PlayerUpdateRequest;
import es.cefa.tv.core.player.model.CommandType;
import es.cefa.tv.core.player.service.PlayerService;
import es.cefa.tv.core.shared.config.CoreProperties;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de administración de reproductores. */
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

  private final PlayerService playerService;
  private final CoreProperties properties;

  /**
   * Instrucciones para instalar el reproductor en una pantalla, con la dirección del repositorio
   * configurada en {@code PLAYER_REPO_URL}.
   *
   * @return dirección del repositorio y comando de instalación
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @GetMapping("/install")
  public PlayerInstallInfo install() {
    String repo = properties.player().repoUrl();
    return new PlayerInstallInfo(
        repo, "git clone " + repo + " cefa-tv-player && cd cefa-tv-player && sudo ./install.sh");
  }

  /**
   * Instrucciones de instalación del reproductor.
   *
   * @param repoUrl dirección del repositorio git
   * @param command comando que se ejecuta en la pantalla
   */
  public record PlayerInstallInfo(String repoUrl, String command) {}

  /**
   * Lista los reproductores visibles, de una sede o de todas.
   *
   * @param siteId sede a filtrar
   * @return los reproductores
   */
  @RequiresPermission(Permission.PLAYER_VIEW)
  @GetMapping
  public List<PlayerResponse> list(@RequestParam(required = false) UUID siteId) {
    return playerService.list(siteId);
  }

  /**
   * Obtiene un reproductor.
   *
   * @param id identificador
   * @return el reproductor
   */
  @RequiresPermission(Permission.PLAYER_VIEW)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.VIEW)
  @GetMapping("/{id}")
  public PlayerResponse get(@PathVariable UUID id) {
    return playerService.get(id);
  }

  /**
   * Modifica un reproductor.
   *
   * @param id identificador
   * @param request campos a cambiar
   * @return el reproductor actualizado
   */
  @RequiresPermission({Permission.PLAYER_MANAGE, Permission.SCHEDULE_MANAGE})
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @PatchMapping("/{id}")
  public PlayerResponse update(
      @PathVariable UUID id, @Valid @RequestBody PlayerUpdateRequest request) {
    return playerService.update(id, request);
  }

  /**
   * Da de baja un reproductor.
   *
   * @param id identificador
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    playerService.delete(id);
  }

  /**
   * Fuerza la entrega de la programación.
   *
   * @param id identificador
   */
  @RequiresPermission({Permission.PLAYER_MANAGE, Permission.SCHEDULE_MANAGE})
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @PostMapping("/{id}/resync")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void resync(@PathVariable UUID id) {
    playerService.resync(id);
  }

  /**
   * Reinicia el reproductor.
   *
   * @param id identificador
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @PostMapping("/{id}/restart")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void restart(@PathVariable UUID id) {
    playerService.command(id, CommandType.RESTART);
  }

  /**
   * Recarga la vista del reproductor.
   *
   * @param id identificador
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @PostMapping("/{id}/reload")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void reload(@PathVariable UUID id) {
    playerService.command(id, CommandType.RELOAD);
  }

  /**
   * Lista el historial de entregas.
   *
   * @param id identificador
   * @return las entregas
   */
  @RequiresPermission(Permission.PLAYER_VIEW)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.VIEW)
  @GetMapping("/{id}/deliveries")
  public List<DeliveryResponse> deliveries(@PathVariable UUID id) {
    return playerService.deliveries(id);
  }
}
