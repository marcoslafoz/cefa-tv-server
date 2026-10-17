package es.cefa.tv.core.auth.service;

import es.cefa.tv.core.auth.dto.UserCreateRequest;
import es.cefa.tv.core.auth.dto.UserDetailResponse;
import es.cefa.tv.core.auth.dto.UserUpdateRequest;
import es.cefa.tv.core.auth.model.AuthProvider;
import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.usergroup.dto.UserGroupRef;
import es.cefa.tv.core.usergroup.model.UserGroup;
import es.cefa.tv.core.usergroup.repository.UserGroupRepository;
import es.cefa.tv.core.usergroup.service.AdminGuard;
import es.cefa.tv.core.usergroup.service.GroupPrivilegeGuard;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administración de usuarios: alta, edición, desactivación, contraseña y borrado. */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository users;
  private final UserGroupRepository userGroups;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticatedUserLoader loader;
  private final AdminGuard adminGuard;
  private final GroupPrivilegeGuard privilegeGuard;

  /**
   * Lista los usuarios.
   *
   * @return los usuarios ordenados por nombre
   */
  @Transactional(readOnly = true)
  public List<UserDetailResponse> list() {
    return users.findAll().stream()
        .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
        .map(this::toResponse)
        .toList();
  }

  /**
   * Obtiene un usuario.
   *
   * @param id identificador
   * @return el usuario
   */
  @Transactional(readOnly = true)
  public UserDetailResponse get(UUID id) {
    return toResponse(find(id));
  }

  /**
   * Crea un usuario.
   *
   * @param request datos
   * @return el usuario creado
   */
  @Transactional
  public UserDetailResponse create(UserCreateRequest request) {
    if (users.existsByUsernameIgnoreCase(request.username())) {
      throw ApiException.conflict("USERNAME_EXISTS", "Ya existe un usuario con ese nombre");
    }
    AuthProvider provider =
        request.authProvider() == null ? AuthProvider.LOCAL : request.authProvider();
    String email = normalizeEmail(request.email());
    requireEmailFree(email, null);
    requireCredentials(provider, email, request.password(), true);
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername(request.username());
    user.setDisplayName(request.displayName());
    user.setEmail(email);
    user.setAuthProvider(provider);
    user.setPasswordHash(
        request.password() == null ? null : passwordEncoder.encode(request.password()));
    user.setEnabled(request.enabled() == null || request.enabled());
    user.setCreatedAt(Instant.now());
    users.save(user);
    applyMembership(user.getId(), request.groupIds());
    return toResponse(user);
  }

  /**
   * Modifica un usuario.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return el usuario actualizado
   */
  @Transactional
  public UserDetailResponse update(UUID id, UserUpdateRequest request) {
    User user = find(id);
    AuthProvider provider =
        request.authProvider() == null ? user.getAuthProvider() : request.authProvider();
    String email = request.email() == null ? user.getEmail() : normalizeEmail(request.email());
    requireEmailFree(email, user.getId());
    boolean needsPassword = user.getPasswordHash() == null && request.password() == null;
    requireCredentials(provider, email, request.password(), needsPassword);
    adminGuard.run(
        () -> {
          user.setEmail(email);
          user.setAuthProvider(provider);
          if (provider == AuthProvider.MICROSOFT) {
            user.setPasswordHash(null);
          } else if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
          }
          user.setDisplayName(request.displayName());
          user.setEnabled(request.enabled());
          if (request.groupIds() != null) {
            applyMembership(id, request.groupIds());
          }
        });
    loader.invalidateAfterCommit(id);
    return toResponse(user);
  }

  /**
   * Restablece la contraseña de un usuario.
   *
   * @param id identificador
   * @param password contraseña nueva
   */
  @Transactional
  public void resetPassword(UUID id, String password) {
    User user = find(id);
    if (!user.getAuthProvider().allowsPassword()) {
      throw ApiException.badRequest(
          "PASSWORD_NOT_ALLOWED", "Este usuario inicia sesión con Microsoft y no tiene contraseña");
    }
    user.setPasswordHash(passwordEncoder.encode(password));
  }

  /**
   * Borra un usuario.
   *
   * @param id identificador
   * @param currentUserId usuario que realiza la acción
   */
  @Transactional
  public void delete(UUID id, UUID currentUserId) {
    User user = find(id);
    if (user.getId().equals(currentUserId)) {
      throw ApiException.conflict("CANNOT_DELETE_SELF", "No puedes borrar tu propio usuario");
    }
    adminGuard.run(
        () -> {
          userGroups.findByMember(id).forEach(group -> group.getMemberIds().remove(id));
          users.delete(user);
        });
    loader.invalidateAfterCommit(id);
  }

  /** Normaliza el correo: sin espacios y en minúsculas; vacío equivale a ausente. */
  private static String normalizeEmail(String email) {
    return email == null || email.isBlank()
        ? null
        : email.trim().toLowerCase(java.util.Locale.ROOT);
  }

  /** Impide que dos usuarios compartan correo. */
  private void requireEmailFree(String email, UUID ownerId) {
    if (email == null) {
      return;
    }
    users
        .findByEmailIgnoreCase(email)
        .filter(other -> !other.getId().equals(ownerId))
        .ifPresent(
            other -> {
              throw ApiException.conflict("EMAIL_EXISTS", "Ya existe un usuario con ese correo");
            });
  }

  /** Comprueba que contraseña y correo sean coherentes con la forma de inicio de sesión. */
  private static void requireCredentials(
      AuthProvider provider, String email, String password, boolean passwordRequired) {
    if (provider.allowsMicrosoft() && email == null) {
      throw ApiException.badRequest(
          "EMAIL_REQUIRED", "El correo es obligatorio para iniciar sesión con Microsoft");
    }
    if (provider == AuthProvider.MICROSOFT && password != null) {
      throw ApiException.badRequest(
          "PASSWORD_NOT_ALLOWED", "Un usuario de solo Microsoft no tiene contraseña");
    }
    if (provider.allowsPassword() && passwordRequired && password == null) {
      throw ApiException.badRequest("PASSWORD_REQUIRED", "La contraseña es obligatoria");
    }
  }

  /** Sustituye los grupos de usuarios del usuario, comprobando que pueda tocar cada grupo. */
  private void applyMembership(UUID userId, List<UUID> groupIds) {
    List<UUID> wanted = groupIds == null ? List.of() : groupIds.stream().distinct().toList();
    List<UserGroup> found = userGroups.findAllById(wanted);
    if (found.size() != wanted.size()) {
      throw ApiException.badRequest("USER_GROUP_NOT_FOUND", "Grupo de usuarios inexistente");
    }
    Set<UUID> current = new HashSet<>(userGroups.findIdsByMember(userId));
    for (UserGroup group : found) {
      if (!current.contains(group.getId())) {
        privilegeGuard.requireCanAssign(group);
        group.getMemberIds().add(userId);
      }
    }
    for (UserGroup group : userGroups.findByMember(userId)) {
      if (!wanted.contains(group.getId())) {
        privilegeGuard.requireCanAssign(group);
        group.getMemberIds().remove(userId);
      }
    }
  }

  /** Busca un usuario o lanza un 404. */
  private User find(UUID id) {
    return users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario"));
  }

  /** Convierte un usuario en su DTO de administración. */
  private UserDetailResponse toResponse(User user) {
    List<UserGroup> groups = userGroups.findByMember(user.getId());
    List<UserGroupRef> refs =
        groups.stream()
            .map(group -> new UserGroupRef(group.getId(), group.getName()))
            .sorted(Comparator.comparing(UserGroupRef::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    return new UserDetailResponse(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getEmail(),
        user.getAuthProvider(),
        user.getMicrosoftObjectId() != null,
        user.isEnabled(),
        refs.stream().map(UserGroupRef::id).toList(),
        refs,
        groups.stream().anyMatch(UserGroup::isAllAccess),
        user.getCreatedAt());
  }
}
