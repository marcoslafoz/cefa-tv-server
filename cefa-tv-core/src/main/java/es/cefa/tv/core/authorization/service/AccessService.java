package es.cefa.tv.core.authorization.service;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Punto único de comprobación de permisos sobre la sesión actual. Delega la decisión en {@link
 * AccessPolicy} y traduce los rechazos a errores 403 con un código estable.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

  private final PlayerRepository players;
  private final PlayerGroupRepository playerGroups;
  private final SiteRepository sites;

  /**
   * Devuelve el usuario de la sesión actual.
   *
   * @return el usuario autenticado
   */
  public AuthenticatedUser current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      return user;
    }
    throw ApiException.unauthorized("UNAUTHENTICATED", "Sesión no válida");
  }

  /**
   * Exige al menos uno de los permisos.
   *
   * @param user usuario
   * @param permissions permisos aceptados
   */
  public void requireAnyPermission(AuthenticatedUser user, Permission... permissions) {
    if (!AccessPolicy.hasAnyPermission(user, permissions)) {
      throw denied(permissions);
    }
  }

  /**
   * Exige al menos uno de los permisos a la sesión actual.
   *
   * @param permissions permisos aceptados
   */
  public void requireAnyPermission(Permission... permissions) {
    requireAnyPermission(current(), permissions);
  }

  /** Exige que la sesión actual sea la de un administrador. */
  public void requireAdmin() {
    if (!current().admin()) {
      throw ApiException.forbidden(
          "ADMIN_REQUIRED", "Esta acción solo la puede realizar un administrador");
    }
  }

  /**
   * Exige un nivel de acceso sobre un reproductor identificado por su id.
   *
   * @param playerId reproductor
   * @param level nivel exigido
   */
  public void requirePlayerLevel(UUID playerId, AccessLevel level) {
    Player player =
        players.findById(playerId).orElseThrow(() -> ApiException.notFound("Reproductor"));
    requirePlayerAccess(current(), player, level);
  }

  /**
   * Exige un permiso y un nivel de acceso sobre un reproductor identificado por su id.
   *
   * @param playerId reproductor
   * @param level nivel exigido
   * @param permissions permisos aceptados
   */
  public void requirePlayer(UUID playerId, AccessLevel level, Permission... permissions) {
    Player player =
        players.findById(playerId).orElseThrow(() -> ApiException.notFound("Reproductor"));
    requirePlayer(player, level, permissions);
  }

  /**
   * Exige un permiso y un nivel de acceso sobre un reproductor.
   *
   * @param player reproductor
   * @param level nivel exigido
   * @param permissions permisos aceptados
   */
  public void requirePlayer(Player player, AccessLevel level, Permission... permissions) {
    AuthenticatedUser user = current();
    requireAnyPermission(user, permissions);
    requirePlayerAccess(user, player, level);
  }

  /**
   * Exige un permiso y un nivel de acceso sobre un grupo de reproductores identificado por su id.
   *
   * @param groupId grupo
   * @param level nivel exigido
   * @param permissions permisos aceptados
   */
  public void requireGroup(UUID groupId, AccessLevel level, Permission... permissions) {
    if (!playerGroups.existsById(groupId)) {
      throw ApiException.notFound("Grupo");
    }
    AuthenticatedUser user = current();
    requireAnyPermission(user, permissions);
    requireGroupLevel(user, groupId, level);
  }

  /**
   * Exige un nivel sobre el grupo de un reproductor, que puede ser nulo (sin grupo).
   *
   * @param groupId grupo o {@code null}
   * @param level nivel exigido
   */
  public void requireGroupLevel(UUID groupId, AccessLevel level) {
    if (groupId != null && !playerGroups.existsById(groupId)) {
      throw ApiException.notFound("Grupo");
    }
    requireGroupLevel(current(), groupId, level);
  }

  /**
   * Indica si la sesión actual alcanza un nivel sobre un reproductor.
   *
   * @param player reproductor
   * @param level nivel exigido
   * @return {@code true} si tiene acceso
   */
  public boolean canAccessPlayer(Player player, AccessLevel level) {
    return AccessPolicy.canAccessPlayer(current(), player.getGroupId(), player.getSiteId(), level);
  }

  /**
   * Indica si la sesión actual puede ver un reproductor: tiene el permiso de ver y una concesión
   * suficiente sobre su grupo.
   *
   * @param player reproductor
   * @return {@code true} si puede verlo
   */
  public boolean canViewPlayer(Player player) {
    return AccessPolicy.canActOnPlayer(
        current(),
        Permission.PLAYER_VIEW,
        player.getGroupId(),
        player.getSiteId(),
        AccessLevel.VIEW);
  }

  /**
   * Indica si la sesión actual puede ver un grupo de reproductores.
   *
   * @param groupId grupo
   * @return {@code true} si puede verlo
   */
  public boolean canViewGroup(UUID groupId) {
    return AccessPolicy.canActOnPlayer(
        current(), Permission.PLAYER_VIEW, groupId, AccessLevel.VIEW);
  }

  /**
   * Indica si la sesión actual alcanza un nivel sobre un grupo de reproductores.
   *
   * @param groupId grupo
   * @param level nivel exigido
   * @return {@code true} si tiene acceso
   */
  public boolean canAccessGroup(UUID groupId, AccessLevel level) {
    return AccessPolicy.canAccessGroup(current(), groupId, level);
  }

  /**
   * Exige un nivel de acceso sobre una sede identificada por su id.
   *
   * @param siteId sede
   * @param level nivel exigido
   */
  public void requireSiteLevel(UUID siteId, AccessLevel level) {
    if (!sites.existsById(siteId)) {
      throw ApiException.notFound("Sede");
    }
    if (!AccessPolicy.canAccessSite(current(), siteId, level)) {
      throw ApiException.forbidden(
          "SCOPE_DENIED", "No tienes acceso " + level.name() + " sobre esta sede");
    }
  }

  /**
   * Indica si la sesión actual alcanza un nivel sobre una sede.
   *
   * @param siteId sede
   * @param level nivel exigido
   * @return {@code true} si tiene acceso por concesión directa sobre la sede
   */
  public boolean canAccessSite(UUID siteId, AccessLevel level) {
    return AccessPolicy.canAccessSite(current(), siteId, level);
  }

  /** Lanza el 403 de alcance si el usuario no llega al nivel sobre el reproductor. */
  private void requirePlayerAccess(AuthenticatedUser user, Player player, AccessLevel level) {
    if (!AccessPolicy.canAccessPlayer(user, player.getGroupId(), player.getSiteId(), level)) {
      throw ApiException.forbidden(
          "SCOPE_DENIED", "No tienes acceso " + level.name() + " sobre este reproductor o grupo");
    }
  }

  /** Lanza el 403 de alcance si el usuario no llega al nivel sobre el grupo. */
  private void requireGroupLevel(AuthenticatedUser user, UUID groupId, AccessLevel level) {
    if (!AccessPolicy.canAccessGroup(user, groupId, level)) {
      throw ApiException.forbidden(
          "SCOPE_DENIED", "No tienes acceso " + level.name() + " sobre este reproductor o grupo");
    }
  }

  /** Construye el 403 de permiso con los permisos aceptados. */
  private ApiException denied(Permission... permissions) {
    String needed = String.join(", ", Arrays.stream(permissions).map(Enum::name).toList());
    return ApiException.forbidden("PERMISSION_DENIED", "Falta el permiso: " + needed);
  }
}
