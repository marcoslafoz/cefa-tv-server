package es.cefa.tv.core.audit.dto;

import es.cefa.tv.core.audit.model.AuditOutcome;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entrada del registro de auditoría.
 *
 * @param id identificador
 * @param at instante de la acción
 * @param userId usuario que la realizó, o {@code null} si fue el sistema o un intento sin
 *     identificar
 * @param username nombre de usuario, o {@code system}
 * @param action acción, con un nombre estable como {@code PLAYLIST_UPDATE}
 * @param entityType tipo de entidad afectada
 * @param entityId identificador de la entidad afectada
 * @param entityName nombre legible de la entidad
 * @param outcome resultado
 * @param details detalle: método, ruta, estado, cambios {@code changes}, petición o motivo
 * @param ip dirección de origen
 * @param userAgent navegador o cliente
 */
public record AuditLogResponse(
    UUID id,
    Instant at,
    UUID userId,
    String username,
    String action,
    String entityType,
    String entityId,
    String entityName,
    AuditOutcome outcome,
    Map<String, Object> details,
    String ip,
    String userAgent) {}
