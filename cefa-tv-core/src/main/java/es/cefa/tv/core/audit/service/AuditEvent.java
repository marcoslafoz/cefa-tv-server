package es.cefa.tv.core.audit.service;

import es.cefa.tv.core.audit.model.AuditOutcome;
import java.util.Map;
import java.util.UUID;

/**
 * Hecho que se registra en la auditoría.
 *
 * @param userId usuario, o {@code null}
 * @param username nombre de usuario
 * @param action acción con nombre estable
 * @param entityType tipo de entidad afectada
 * @param entityId identificador de la entidad
 * @param entityName nombre legible de la entidad
 * @param outcome resultado
 * @param details detalle adicional sin secretos
 * @param ip dirección de origen
 * @param userAgent cliente
 */
public record AuditEvent(
    UUID userId,
    String username,
    String action,
    String entityType,
    String entityId,
    String entityName,
    AuditOutcome outcome,
    Map<String, Object> details,
    String ip,
    String userAgent) {

  /** Nombre de usuario con el que se registran las acciones automáticas del sistema. */
  public static final String SYSTEM = "system";

  /**
   * Crea un hecho realizado por el sistema.
   *
   * @param action acción
   * @param entityType tipo de entidad
   * @param entityId identificador de la entidad
   * @param entityName nombre de la entidad
   * @param outcome resultado
   * @param details detalle
   * @return el hecho
   */
  public static AuditEvent system(
      String action,
      String entityType,
      String entityId,
      String entityName,
      AuditOutcome outcome,
      Map<String, Object> details) {
    return new AuditEvent(
        null, SYSTEM, action, entityType, entityId, entityName, outcome, details, null, null);
  }
}
