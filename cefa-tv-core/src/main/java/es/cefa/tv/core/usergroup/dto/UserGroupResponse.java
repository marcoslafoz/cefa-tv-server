package es.cefa.tv.core.usergroup.dto;

import es.cefa.tv.core.auth.dto.GrantDto;
import java.util.List;
import java.util.UUID;

/**
 * Grupo de usuarios.
 *
 * @param id identificador
 * @param name nombre
 * @param description descripción
 * @param builtin si es un grupo integrado, que no se puede modificar ni borrar
 * @param allAccess si da acceso a todos los reproductores y sedes con todos los permisos
 * @param permissions permisos del grupo, ordenados
 * @param memberIds usuarios que lo componen
 * @param memberCount número de miembros
 * @param grants concesiones sobre grupos de reproductores o sedes
 */
public record UserGroupResponse(
    UUID id,
    String name,
    String description,
    boolean builtin,
    boolean allAccess,
    List<String> permissions,
    List<UUID> memberIds,
    int memberCount,
    List<GrantDto> grants) {}
