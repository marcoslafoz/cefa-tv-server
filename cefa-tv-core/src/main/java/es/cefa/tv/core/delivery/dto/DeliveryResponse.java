package es.cefa.tv.core.delivery.dto;

import es.cefa.tv.core.delivery.model.DeliveryStatus;
import java.time.Instant;

/**
 * Entrega de una versión.
 *
 * @param version versión
 * @param status estado
 * @param error motivo del fallo
 * @param createdAt cuándo se calculó
 * @param updatedAt última actualización
 * @param deliveredAt cuándo se aplicó
 */
public record DeliveryResponse(
    long version,
    DeliveryStatus status,
    String error,
    Instant createdAt,
    Instant updatedAt,
    Instant deliveredAt) {}
