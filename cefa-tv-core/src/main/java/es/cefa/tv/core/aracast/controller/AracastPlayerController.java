package es.cefa.tv.core.aracast.controller;

import es.cefa.tv.core.aracast.dto.AracastCredentialsRequest;
import es.cefa.tv.core.aracast.dto.AracastPlayerResponse;
import es.cefa.tv.core.aracast.dto.CreateAracastPlayerRequest;
import es.cefa.tv.core.aracast.dto.PlayerStatus;
import es.cefa.tv.core.aracast.service.AracastPlayerService;
import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.annotation.RequiresScope;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.player.dto.PlayerResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Alta de reproductores Aracast. */
@RestController
@RequestMapping("/api/v1/players/aracast")
@RequiredArgsConstructor
@RequiresPermission(Permission.PLAYER_MANAGE)
public class AracastPlayerController {

  private final AracastPlayerService service;

  /**
   * Consulta un player sin darlo de alta.
   *
   * @param host IP o nombre del player
   * @return su estado
   */
  @GetMapping("/probe")
  public PlayerStatus probe(@RequestParam String host) {
    return service.probe(host);
  }

  /**
   * Da de alta un player Aracast.
   *
   * @param request datos del alta
   * @return el reproductor creado
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlayerResponse create(@Valid @RequestBody CreateAracastPlayerRequest request) {
    return service.create(request);
  }

  /**
   * Datos de conexión de un Aracast, sin contraseñas.
   *
   * @param id reproductor
   * @return los datos
   */
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.VIEW)
  @GetMapping("/{id}")
  public AracastPlayerResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  /**
   * Cambia las contraseñas propias de un Aracast.
   *
   * @param id reproductor
   * @param request contraseñas nuevas
   * @return los datos actualizados
   */
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
  @PutMapping("/{id}/credentials")
  public AracastPlayerResponse updateCredentials(
      @PathVariable UUID id, @Valid @RequestBody AracastCredentialsRequest request) {
    return service.updateCredentials(id, request);
  }
}
