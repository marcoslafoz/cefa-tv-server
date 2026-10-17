package es.cefa.tv.core.audit.service;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Punto de entrada para registrar acciones en la auditoría. Sanea los detalles y nunca interrumpe
 * la operación auditada si el registro falla.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditWriter writer;
  private final AuditSanitizer sanitizer;

  /**
   * Registra un hecho.
   *
   * @param event hecho; sus detalles se sanean antes de guardarse
   */
  public void record(AuditEvent event) {
    try {
      writer.write(
          new AuditEvent(
              event.userId(),
              event.username(),
              event.action(),
              event.entityType(),
              event.entityId(),
              AuditSanitizer.clip(event.entityName()),
              event.outcome(),
              sanitizer.map(event.details()),
              event.ip(),
              event.userAgent()));
    } catch (RuntimeException e) {
      log.error("No se pudo registrar la auditoría de {}", event.action(), e);
    }
  }

  /**
   * Registra una acción de un usuario.
   *
   * @param user usuario autenticado
   * @param client origen de la acción
   * @param action acción
   * @param entityType tipo de entidad
   * @param entityId identificador de la entidad
   * @param entityName nombre de la entidad
   * @param outcome resultado
   * @param details detalle
   */
  public void recordUser(
      AuthenticatedUser user,
      AuditClient client,
      String action,
      String entityType,
      String entityId,
      String entityName,
      AuditOutcome outcome,
      Map<String, Object> details) {
    record(
        new AuditEvent(
            user.id(),
            user.username(),
            action,
            entityType,
            entityId,
            entityName,
            outcome,
            details,
            client.ip(),
            client.userAgent()));
  }

  /**
   * Registra una acción automática del sistema.
   *
   * @param action acción
   * @param entityType tipo de entidad
   * @param entityId identificador de la entidad
   * @param entityName nombre de la entidad
   * @param outcome resultado
   * @param details detalle
   */
  public void recordSystem(
      String action,
      String entityType,
      String entityId,
      String entityName,
      AuditOutcome outcome,
      Map<String, Object> details) {
    record(AuditEvent.system(action, entityType, entityId, entityName, outcome, details));
  }
}
