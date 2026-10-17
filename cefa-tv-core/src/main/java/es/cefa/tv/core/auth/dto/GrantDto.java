package es.cefa.tv.core.auth.dto;

import es.cefa.tv.core.authorization.model.AccessLevel;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Nivel de acceso sobre un grupo de reproductores o sobre una sede; exactamente uno de los dos.
 *
 * @param playerGroupId grupo de reproductores, o {@code null} si la concesión es sobre una sede
 * @param siteId sede, o {@code null} si la concesión es sobre un grupo de reproductores
 * @param level nivel de acceso
 */
public record GrantDto(UUID playerGroupId, UUID siteId, @NotNull AccessLevel level) {}
