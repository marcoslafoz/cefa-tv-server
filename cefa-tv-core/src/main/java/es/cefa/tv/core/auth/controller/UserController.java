package es.cefa.tv.core.auth.controller;

import es.cefa.tv.core.auth.dto.PasswordResetRequest;
import es.cefa.tv.core.auth.dto.UserCreateRequest;
import es.cefa.tv.core.auth.dto.UserDetailResponse;
import es.cefa.tv.core.auth.dto.UserUpdateRequest;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.auth.service.UserService;
import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de administración de usuarios. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@RequiresPermission(Permission.USER_MANAGE)
public class UserController {

  private final UserService userService;

  /**
   * Lista los usuarios.
   *
   * @return los usuarios
   */
  @GetMapping
  public List<UserDetailResponse> list() {
    return userService.list();
  }

  /**
   * Obtiene un usuario.
   *
   * @param id identificador
   * @return el usuario
   */
  @GetMapping("/{id}")
  public UserDetailResponse get(@PathVariable UUID id) {
    return userService.get(id);
  }

  /**
   * Crea un usuario.
   *
   * @param request datos
   * @return el usuario creado
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserDetailResponse create(@Valid @RequestBody UserCreateRequest request) {
    return userService.create(request);
  }

  /**
   * Modifica un usuario.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return el usuario actualizado
   */
  @PutMapping("/{id}")
  public UserDetailResponse update(
      @PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
    return userService.update(id, request);
  }

  /**
   * Restablece la contraseña de un usuario.
   *
   * @param id identificador
   * @param request contraseña nueva
   */
  @PostMapping("/{id}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(
      @PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request) {
    userService.resetPassword(id, request.password());
  }

  /**
   * Borra un usuario.
   *
   * @param id identificador
   * @param principal usuario que realiza la acción
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    userService.delete(id, principal.id());
  }
}
