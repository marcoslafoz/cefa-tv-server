package es.cefa.tv.core.auth.security;

import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.usergroup.dto.UserGroupRef;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Principal de una sesión de administración con sus permisos y concesiones ya resueltos como unión
 * de los de todos sus grupos.
 *
 * @param id identificador del usuario
 * @param username nombre de usuario
 * @param displayName nombre visible
 * @param groups grupos de usuarios a los que pertenece
 * @param admin si pertenece a algún grupo con acceso total a todos los reproductores y sedes
 * @param permissions permisos efectivos; todos si es administrador
 * @param grants nivel de acceso efectivo por grupo de reproductores
 * @param siteGrants nivel de acceso efectivo por sede
 * @param email correo del usuario, o {@code null}
 * @param authProvider forma de inicio de sesión: LOCAL, MICROSOFT o HYBRID
 */
public record AuthenticatedUser(
    UUID id,
    String username,
    String displayName,
    List<UserGroupRef> groups,
    boolean admin,
    Set<Permission> permissions,
    Map<UUID, AccessLevel> grants,
    Map<UUID, AccessLevel> siteGrants,
    String email,
    String authProvider) {}
