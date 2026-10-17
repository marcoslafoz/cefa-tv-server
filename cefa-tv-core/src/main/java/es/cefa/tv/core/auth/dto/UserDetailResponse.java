package es.cefa.tv.core.auth.dto;

import es.cefa.tv.core.auth.model.AuthProvider;
import es.cefa.tv.core.usergroup.dto.UserGroupRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Usuario para la administración.
 *
 * @param id identificador
 * @param username nombre de usuario
 * @param displayName nombre visible
 * @param email correo, o {@code null}
 * @param authProvider forma de inicio de sesión
 * @param microsoftLinked si el usuario ya está enlazado a una cuenta de Microsoft
 * @param enabled si está activo
 * @param groupIds identificadores de los grupos de usuarios a los que pertenece
 * @param groups grupos de usuarios a los que pertenece
 * @param admin si pertenece a algún grupo con acceso total
 * @param createdAt fecha de alta
 */
public record UserDetailResponse(
    UUID id,
    String username,
    String displayName,
    String email,
    AuthProvider authProvider,
    boolean microsoftLinked,
    boolean enabled,
    List<UUID> groupIds,
    List<UserGroupRef> groups,
    boolean admin,
    Instant createdAt) {}
