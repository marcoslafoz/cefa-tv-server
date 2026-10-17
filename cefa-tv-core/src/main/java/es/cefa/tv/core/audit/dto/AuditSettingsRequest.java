package es.cefa.tv.core.audit.dto;

import es.cefa.tv.core.audit.model.AuditSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cambio de los ajustes de auditoría.
 *
 * @param retentionDays días que se conservan las entradas
 */
public record AuditSettingsRequest(
    @Min(AuditSettings.MIN_RETENTION_DAYS) @Max(AuditSettings.MAX_RETENTION_DAYS)
        int retentionDays) {}
