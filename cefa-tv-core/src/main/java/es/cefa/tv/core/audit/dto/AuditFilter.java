package es.cefa.tv.core.audit.dto;

import es.cefa.tv.core.audit.model.AuditOutcome;
import java.time.Instant;
import java.util.UUID;

/**
 * Filtros de consulta del registro de auditoría; los nulos no filtran.
 *
 * @param from inicio, incluido
 * @param to fin, excluido
 * @param userId usuario
 * @param action acción exacta
 * @param entityType tipo de entidad
 * @param entityId identificador de la entidad
 * @param outcome resultado
 * @param q texto buscado en usuario, acción, entidad y nombre
 */
public record AuditFilter(
    Instant from,
    Instant to,
    UUID userId,
    String action,
    String entityType,
    String entityId,
    AuditOutcome outcome,
    String q) {}
