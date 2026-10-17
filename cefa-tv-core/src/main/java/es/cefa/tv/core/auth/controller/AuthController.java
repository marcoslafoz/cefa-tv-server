package es.cefa.tv.core.auth.controller;

import es.cefa.tv.core.auth.dto.LoginRequest;
import es.cefa.tv.core.auth.dto.PasswordChangeRequest;
import es.cefa.tv.core.auth.dto.UserResponse;
import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.auth.security.SessionCookies;
import es.cefa.tv.core.auth.service.AuthService;
import es.cefa.tv.core.shared.exception.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de sesión de la interfaz de administración. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final SessionCookies cookies;

  /**
   * Inicia sesión y establece la cookie.
   *
   * @param request credenciales
   * @return el usuario autenticado
   */
  @PostMapping("/login")
  public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
    User user = authService.authenticate(request.username(), request.password());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookies.create(authService.issueToken(user)).toString())
        .body(authService.describe(user.getId()));
  }

  /**
   * Cierra la sesión borrando la cookie.
   *
   * @return respuesta vacía
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }

  /**
   * Devuelve el usuario de la sesión actual con sus permisos.
   *
   * @param principal usuario autenticado
   * @return el usuario
   */
  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
    if (principal == null) {
      throw ApiException.unauthorized("UNAUTHENTICATED", "Sesión no válida");
    }
    return AuthService.toResponse(principal);
  }

  /**
   * Cambia la contraseña del usuario de la sesión actual.
   *
   * @param principal usuario autenticado
   * @param request contraseña actual y nueva
   */
  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody PasswordChangeRequest request) {
    if (principal == null) {
      throw ApiException.unauthorized("UNAUTHENTICATED", "Sesión no válida");
    }
    authService.changePassword(principal.id(), request);
  }
}
