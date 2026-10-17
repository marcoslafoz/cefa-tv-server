package es.cefa.tv.core.audit.service;

import es.cefa.tv.core.audit.model.AuditLog;
import es.cefa.tv.core.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda las entradas de auditoría en una transacción propia, para que sobrevivan a un rollback.
 */
@Component
@RequiredArgsConstructor
public class AuditWriter {

  private final AuditLogRepository logs;

  /**
   * Persiste una entrada.
   *
   * @param event hecho a registrar, ya saneado
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void write(AuditEvent event) {
    AuditLog log = new AuditLog();
    log.setId(UUID.randomUUID());
    log.setAt(Instant.now());
    log.setUserId(event.userId());
    log.setUsername(cut(event.username(), 150));
    log.setAction(cut(event.action(), 100));
    log.setEntityType(cut(event.entityType(), 50));
    log.setEntityId(cut(event.entityId(), 100));
    log.setEntityName(cut(event.entityName(), 255));
    log.setOutcome(event.outcome());
    log.setDetails(event.details());
    log.setIp(cut(event.ip(), 100));
    log.setUserAgent(cut(event.userAgent(), 400));
    logs.save(log);
  }

  /** Recorta un texto a la longitud de su columna. */
  private static String cut(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
