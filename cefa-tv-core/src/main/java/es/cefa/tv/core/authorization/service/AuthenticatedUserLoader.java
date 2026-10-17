package es.cefa.tv.core.authorization.service;

import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.usergroup.dto.UserGroupRef;
import es.cefa.tv.core.usergroup.model.AccessGrant;
import es.cefa.tv.core.usergroup.model.UserGroup;
import es.cefa.tv.core.usergroup.repository.AccessGrantRepository;
import es.cefa.tv.core.usergroup.repository.UserGroupRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Construye el principal de una sesión como unión de los permisos y concesiones de todos los grupos
 * del usuario. El resultado se guarda en una caché con caducidad corta que se invalida al editar
 * usuarios, grupos o membresías.
 */
@Service
@RequiredArgsConstructor
public class AuthenticatedUserLoader {

  private static final Duration TTL = Duration.ofMinutes(5);

  private final UserRepository users;
  private final UserGroupRepository userGroups;
  private final AccessGrantRepository grants;
  private final Map<UUID, CachedUser> cache = new ConcurrentHashMap<>();

  /** Principal guardado en caché con su instante de caducidad. */
  private record CachedUser(AuthenticatedUser user, Instant expiresAt) {}

  /**
   * Carga un usuario activo con sus permisos efectivos.
   *
   * @param userId identificador del usuario
   * @return el principal, o vacío si el usuario no existe o está desactivado
   */
  public Optional<AuthenticatedUser> load(UUID userId) {
    CachedUser hit = cache.get(userId);
    if (hit != null && hit.expiresAt().isAfter(Instant.now())) {
      return Optional.of(hit.user());
    }
    Optional<AuthenticatedUser> loaded =
        users.findById(userId).filter(User::isEnabled).map(this::build);
    loaded.ifPresentOrElse(
        user -> cache.put(userId, new CachedUser(user, Instant.now().plus(TTL))),
        () -> cache.remove(userId));
    return loaded;
  }

  /**
   * Olvida a un usuario cuando termina la transacción en curso, o de inmediato si no hay ninguna.
   *
   * @param userId usuario cuyos permisos pueden haber cambiado
   */
  public void invalidateAfterCommit(UUID userId) {
    afterCommit(() -> cache.remove(userId));
  }

  /** Olvida a todos los usuarios cuando termina la transacción en curso, o de inmediato. */
  public void invalidateAllAfterCommit() {
    afterCommit(cache::clear);
  }

  /** Ejecuta la acción tras confirmar la transacción actual o ahora si no hay transacción. */
  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }

  /** Resuelve grupos, permisos y concesiones de un usuario como unión de sus grupos. */
  private AuthenticatedUser build(User user) {
    List<UserGroup> groups = userGroups.findByMember(user.getId());
    boolean admin = groups.stream().anyMatch(UserGroup::isAllAccess);
    Set<Permission> permissions = EnumSet.noneOf(Permission.class);
    groups.forEach(group -> permissions.addAll(group.permissionSet()));
    Map<UUID, AccessLevel> groupGrants = new HashMap<>();
    Map<UUID, AccessLevel> siteGrants = new HashMap<>();
    if (!admin && !groups.isEmpty()) {
      List<UUID> ids = groups.stream().map(UserGroup::getId).toList();
      for (AccessGrant grant : grants.findByUserGroupIdIn(ids)) {
        if (grant.getPlayerGroupId() != null) {
          groupGrants.merge(grant.getPlayerGroupId(), grant.getLevel(), AccessLevel::max);
        } else {
          siteGrants.merge(grant.getSiteId(), grant.getLevel(), AccessLevel::max);
        }
      }
    }
    return new AuthenticatedUser(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        groups.stream()
            .map(group -> new UserGroupRef(group.getId(), group.getName()))
            .sorted(Comparator.comparing(UserGroupRef::name, String.CASE_INSENSITIVE_ORDER))
            .toList(),
        admin,
        admin ? EnumSet.allOf(Permission.class) : permissions,
        groupGrants,
        siteGrants,
        user.getEmail(),
        user.getAuthProvider().name());
  }
}
