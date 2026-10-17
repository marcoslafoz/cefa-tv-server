package es.cefa.tv.core.usergroup.dto;

import es.cefa.tv.core.auth.dto.GrantDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Creación o modificación de un grupo de usuarios.
 *
 * @param name nombre único
 * @param description descripción opcional
 * @param memberIds usuarios que lo componen; si es {@code null} al modificar se conservan
 * @param permissions permisos del grupo (por ejemplo {@code SCHEDULE_MANAGE}); ignorados si el
 *     grupo da acceso total, que los tiene todos
 * @param allAccess si el grupo da acceso a todos los reproductores y sedes con todos los permisos;
 *     solo lo puede cambiar un administrador y, si es {@code null}, se conserva
 * @param grants concesiones sobre grupos de reproductores o sedes con nivel {@code VIEW} u {@code
 *     OPERATE}; si es {@code null} al modificar se conservan
 */
public record UserGroupRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 500) String description,
    List<UUID> memberIds,
    List<String> permissions,
    Boolean allAccess,
    @Valid List<GrantDto> grants) {}
