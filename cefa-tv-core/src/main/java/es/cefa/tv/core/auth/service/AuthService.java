package es.cefa.tv.core.auth.service;

import es.cefa.tv.core.auth.dto.GrantDto;
import es.cefa.tv.core.auth.dto.PasswordChangeRequest;
import es.cefa.tv.core.auth.dto.UserResponse;
import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
import es.cefa.tv.core.shared.exception.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lógica de inicio de sesión y de cambio de la propia contraseña. */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticatedUserLoader loader;

  /**
   * Verifica las credenciales y devuelve el usuario.
   *
   * @param username usuario
   * @param password contraseña en claro
   * @return el usuario autenticado
   */
  public User authenticate(String username, String password) {
    return users
        .findByUsername(username)
        .filter(User::isEnabled)
        .filter(user -> user.getAuthProvider().allowsPassword() && user.getPasswordHash() != null)
        .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
        .orElseThrow(
            () ->
                ApiException.unauthorized(
                    "INVALID_CREDENTIALS", "Usuario o contraseña incorrectos"));
  }

  /**
   * Genera el JWT de sesión de un usuario.
   *
   * @param user usuario autenticado
   * @return el token
   */
  public String issueToken(User user) {
    return jwtService.issue(user);
  }

  /**
   * Devuelve el usuario con sus permisos efectivos.
   *
   * @param userId identificador
   * @return el DTO
   */
  @Transactional(readOnly = true)
  public UserResponse describe(UUID userId) {
    return loader
        .load(userId)
        .map(AuthService::toResponse)
        .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Sesión no válida"));
  }

  /**
   * Cambia la contraseña del propio usuario.
   *
   * @param userId usuario
   * @param request contraseña actual y nueva
   */
  @Transactional
  public void changePassword(UUID userId, PasswordChangeRequest request) {
    User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario"));
    if (!user.getAuthProvider().allowsPassword()) {
      throw ApiException.badRequest(
          "PASSWORD_NOT_ALLOWED", "Este usuario inicia sesión con Microsoft y no tiene contraseña");
    }
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw ApiException.badRequest(
          "INVALID_CURRENT_PASSWORD", "La contraseña actual no es válida");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
  }

  /**
   * Convierte el principal en su DTO público.
   *
   * @param user principal con permisos resueltos
   * @return el DTO
   */
  public static UserResponse toResponse(AuthenticatedUser user) {
    List<String> permissions = user.permissions().stream().map(Enum::name).sorted().toList();
    List<GrantDto> grants =
        Stream.concat(
                user.grants().entrySet().stream()
                    .map(entry -> new GrantDto(entry.getKey(), null, entry.getValue())),
                user.siteGrants().entrySet().stream()
                    .map(entry -> new GrantDto(null, entry.getKey(), entry.getValue())))
            .sorted(
                Comparator.comparing(
                    grant ->
                        grant.playerGroupId() != null
                            ? "g" + grant.playerGroupId()
                            : "s" + grant.siteId()))
            .toList();
    return new UserResponse(
        user.id(),
        user.username(),
        user.displayName(),
        user.email(),
        user.authProvider(),
        user.groups(),
        permissions,
        grants,
        user.admin());
  }
}
