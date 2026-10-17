package es.cefa.tv.core.authorization.service;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import java.util.Arrays;
import java.util.UUID;

/**
 * Regla de decisión de permisos: un usuario puede actuar sobre un reproductor si su rol tiene el
 * permiso y es administrador o tiene una concesión suficiente sobre el grupo del reproductor. Un
 * reproductor sin grupo solo lo gestiona un administrador.
 */
public final class AccessPolicy {

  /** Evita la instanciación de la clase de utilidades. */
  private AccessPolicy() {}

  /**
   * Indica si el usuario tiene un permiso.
   *
   * @param user usuario
   * @param permission permiso exigido
   * @return {@code true} si es administrador o su rol lo incluye
   */
  public static boolean hasPermission(AuthenticatedUser user, Permission permission) {
    return user.admin() || user.permissions().contains(permission);
  }

  /**
   * Indica si el usuario tiene al menos uno de los permisos.
   *
   * @param user usuario
   * @param permissions permisos aceptados
   * @return {@code true} si tiene alguno
   */
  public static boolean hasAnyPermission(AuthenticatedUser user, Permission... permissions) {
    return Arrays.stream(permissions).anyMatch(permission -> hasPermission(user, permission));
  }

  /**
   * Indica si el usuario alcanza un nivel sobre un grupo de reproductores.
   *
   * @param user usuario
   * @param groupId grupo del reproductor, o {@code null} si no tiene
   * @param level nivel exigido
   * @return {@code true} si es administrador o tiene una concesión suficiente
   */
  public static boolean canAccessGroup(AuthenticatedUser user, UUID groupId, AccessLevel level) {
    if (user.admin()) {
      return true;
    }
    if (groupId == null) {
      return false;
    }
    AccessLevel granted = user.grants().get(groupId);
    return granted != null && granted.covers(level);
  }

  /**
   * Indica si el usuario alcanza un nivel sobre una sede.
   *
   * @param user usuario
   * @param siteId sede, o {@code null} si no hay
   * @param level nivel exigido
   * @return {@code true} si es administrador o tiene una concesión suficiente sobre la sede
   */
  public static boolean canAccessSite(AuthenticatedUser user, UUID siteId, AccessLevel level) {
    if (user.admin()) {
      return true;
    }
    if (siteId == null) {
      return false;
    }
    AccessLevel granted = user.siteGrants().get(siteId);
    return granted != null && granted.covers(level);
  }

  /**
   * Indica si el usuario alcanza un nivel sobre un reproductor: es administrador o tiene una
   * concesión suficiente sobre su grupo o sobre su sede.
   *
   * @param user usuario
   * @param groupId grupo del reproductor, o {@code null}
   * @param siteId sede del reproductor, o {@code null}
   * @param level nivel exigido
   * @return {@code true} si tiene acceso
   */
  public static boolean canAccessPlayer(
      AuthenticatedUser user, UUID groupId, UUID siteId, AccessLevel level) {
    return canAccessGroup(user, groupId, level) || canAccessSite(user, siteId, level);
  }

  /**
   * Indica si el usuario puede realizar una acción sobre un reproductor.
   *
   * @param user usuario
   * @param permission permiso exigido por la acción
   * @param playerGroupId grupo del reproductor, o {@code null} si no tiene
   * @param level nivel exigido sobre el grupo
   * @return {@code true} si cumple el permiso y el alcance
   */
  public static boolean canActOnPlayer(
      AuthenticatedUser user, Permission permission, UUID playerGroupId, AccessLevel level) {
    return canActOnPlayer(user, permission, playerGroupId, null, level);
  }

  /**
   * Indica si el usuario puede realizar una acción sobre un reproductor teniendo en cuenta su grupo
   * y su sede.
   *
   * @param user usuario
   * @param permission permiso exigido por la acción
   * @param playerGroupId grupo del reproductor, o {@code null}
   * @param playerSiteId sede del reproductor, o {@code null}
   * @param level nivel exigido
   * @return {@code true} si cumple el permiso y el alcance por grupo o por sede
   */
  public static boolean canActOnPlayer(
      AuthenticatedUser user,
      Permission permission,
      UUID playerGroupId,
      UUID playerSiteId,
      AccessLevel level) {
    return hasPermission(user, permission)
        && canAccessPlayer(user, playerGroupId, playerSiteId, level);
  }
}
