package es.cefa.tv.core.auth.dto;

import es.cefa.tv.core.usergroup.dto.UserGroupRef;
import java.util.List;
import java.util.UUID;

/**
 * Usuario autenticado con sus permisos efectivos, que son la unión de los de sus grupos.
 *
 * @param id identificador
 * @param username nombre de usuario
 * @param displayName nombre visible
 * @param email correo del usuario, o {@code null}
 * @param authProvider forma de inicio de sesión: LOCAL, MICROSOFT o HYBRID
 * @param groups grupos de usuarios a los que pertenece
 * @param permissions permisos efectivos; todos si es administrador
 * @param grants nivel de acceso efectivo por grupo de reproductores o sede; vacío si es
 *     administrador, que accede a todo
 * @param admin si pertenece a algún grupo con acceso total
 */
public record UserResponse(
    UUID id,
    String username,
    String displayName,
    String email,
    String authProvider,
    List<UserGroupRef> groups,
    List<String> permissions,
    List<GrantDto> grants,
    boolean admin) {}
