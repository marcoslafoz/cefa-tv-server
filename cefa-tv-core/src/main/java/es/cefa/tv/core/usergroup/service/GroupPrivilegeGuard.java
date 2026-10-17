package es.cefa.tv.core.usergroup.service;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.usergroup.model.UserGroup;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Evita la escalada de privilegios: quien gestiona usuarios sin ser administrador no puede dar a
 * otros, ni a sí mismo, más permisos de los que tiene ni tocar los grupos con acceso total.
 */
@Component
@RequiredArgsConstructor
public class GroupPrivilegeGuard {

  private final AccessService access;

  /**
   * Exige poder definir un grupo con esos permisos y ese acceso total.
   *
   * @param permissions permisos del grupo
   * @param allAccess si el grupo da acceso total
   */
  public void requireCanDefine(Set<Permission> permissions, boolean allAccess) {
    AuthenticatedUser user = access.current();
    if (user.admin()) {
      return;
    }
    if (allAccess) {
      throw ApiException.forbidden(
          "ADMIN_REQUIRED", "Solo un administrador puede dar acceso total a un grupo");
    }
    if (!user.permissions().containsAll(permissions)) {
      throw escalation();
    }
  }

  /**
   * Exige poder incorporar o retirar usuarios de un grupo.
   *
   * @param group grupo cuya membresía cambia
   */
  public void requireCanAssign(UserGroup group) {
    AuthenticatedUser user = access.current();
    if (user.admin()) {
      return;
    }
    if (group.isAllAccess()) {
      throw ApiException.forbidden(
          "ADMIN_REQUIRED", "Solo un administrador puede cambiar los miembros de este grupo");
    }
    if (!user.permissions().containsAll(group.permissionSet())) {
      throw escalation();
    }
  }

  /** Construye el 403 de escalada de privilegios. */
  private static ApiException escalation() {
    return ApiException.forbidden(
        "PRIVILEGE_ESCALATION", "No puedes dar permisos que tú no tienes");
  }
}
