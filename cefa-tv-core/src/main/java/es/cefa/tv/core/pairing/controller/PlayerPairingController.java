package es.cefa.tv.core.pairing.controller;

import es.cefa.tv.core.pairing.dto.PairingRequest;
import es.cefa.tv.core.pairing.dto.PairingRequestResponse;
import es.cefa.tv.core.pairing.dto.PairingStatusResponse;
import es.cefa.tv.core.pairing.service.PairingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints sin token con los que un reproductor sin emparejar obtiene y consulta su código. */
@RestController
@RequestMapping("/player-api/pairing")
@RequiredArgsConstructor
public class PlayerPairingController {

  private final PairingService pairingService;

  /**
   * Pide un código de emparejamiento.
   *
   * @param request datos del reproductor
   * @param servletRequest petición HTTP
   * @return el código, el secreto y la caducidad
   */
  @PostMapping("/request")
  @ResponseStatus(HttpStatus.CREATED)
  public PairingRequestResponse request(
      @RequestBody PairingRequest request, HttpServletRequest servletRequest) {
    return pairingService.request(request, servletRequest.getRemoteAddr());
  }

  /**
   * Consulta el estado de la petición de emparejamiento.
   *
   * @param id identificador de la petición
   * @param secret secreto entregado al pedir el código
   * @return el estado y, si está aprobada, las credenciales
   */
  @GetMapping("/{id}")
  public PairingStatusResponse status(
      @PathVariable UUID id,
      @RequestHeader(value = "X-Pairing-Secret", required = false) String secret) {
    return pairingService.poll(id, secret);
  }
}
