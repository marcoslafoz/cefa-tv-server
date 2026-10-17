package es.cefa.tv.core.audit.dto;

/**
 * Ajustes de auditoría.
 *
 * @param retentionDays días que se conservan las entradas
 * @param minRetentionDays mínimo permitido
 * @param maxRetentionDays máximo permitido
 */
public record AuditSettingsResponse(
    int retentionDays, int minRetentionDays, int maxRetentionDays) {}
