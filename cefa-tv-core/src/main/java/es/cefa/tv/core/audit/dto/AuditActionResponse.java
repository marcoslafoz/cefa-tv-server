package es.cefa.tv.core.audit.dto;

/**
 * Acción del catálogo de auditoría para el filtro.
 *
 * @param action nombre estable de la acción
 * @param entityType tipo de entidad que afecta, o {@code null}
 */
public record AuditActionResponse(String action, String entityType) {}
