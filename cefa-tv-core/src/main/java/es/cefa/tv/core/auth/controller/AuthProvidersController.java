package es.cefa.tv.core.auth.controller;

import es.cefa.tv.core.microsoft.dto.AuthProvidersResponse;
import es.cefa.tv.core.shared.config.CoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Informa, sin autenticación, de las formas de iniciar sesión disponibles. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthProvidersController {

  private final CoreProperties properties;

  /**
   * Devuelve las formas de iniciar sesión activas.
   *
   * @return si hay formulario local y si Microsoft está configurado
   */
  @GetMapping("/providers")
  public AuthProvidersResponse providers() {
    return new AuthProvidersResponse(true, properties.microsoft().enabled());
  }
}
