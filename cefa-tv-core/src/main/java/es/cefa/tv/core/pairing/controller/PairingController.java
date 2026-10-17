package es.cefa.tv.core.pairing.controller;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.pairing.dto.ClaimRequest;
import es.cefa.tv.core.pairing.dto.PendingPairingResponse;
import es.cefa.tv.core.pairing.service.PairingService;
import es.cefa.tv.core.player.dto.PlayerResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de administración para emparejar reproductores por su código. */
@RestController
@RequestMapping("/api/v1/pairings")
@RequiredArgsConstructor
@RequiresPermission(Permission.PLAYER_MANAGE)
public class PairingController {

  private final PairingService pairingService;

  /**
   * Lista los reproductores que esperan emparejarse.
   *
   * @return las peticiones vigentes, sin sus códigos
   */
  @GetMapping("/pending")
  public List<PendingPairingResponse> pending() {
    return pairingService.pending();
  }

  /**
   * Empareja el reproductor que muestra el código.
   *
   * @param request código, nombre y grupo
   * @param principal usuario que empareja
   * @return el reproductor creado
   */
  @PostMapping("/claim")
  @ResponseStatus(HttpStatus.CREATED)
  public PlayerResponse claim(
      @Valid @RequestBody ClaimRequest request,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return pairingService.claim(request, principal);
  }
}
