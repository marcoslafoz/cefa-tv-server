package es.cefa.tv.core.audit.service;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.repository.AuditLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Purga cada día las entradas de auditoría más antiguas que la retención configurada. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionScheduler {

  private final AuditLogRepository logs;
  private final AuditSettingsService settings;
  private final AuditService audit;

  /**
   * Borra las entradas vencidas y deja constancia de la purga en la propia auditoría.
   *
   * @return el número de entradas borradas
   */
  @Scheduled(cron = "0 30 3 * * *")
  @Transactional
  public int purge() {
    int days = settings.retentionDays();
    int deleted = logs.deleteByAtBefore(Instant.now().minus(Duration.ofDays(days)));
    audit.recordSystem(
        "AUDIT_PURGE",
        "SETTINGS",
        null,
        "audit",
        AuditOutcome.SUCCESS,
        Map.of("deleted", deleted, "retentionDays", days));
    log.info("Auditoría purgada: {} entradas con retención de {} días", deleted, days);
    return deleted;
  }
}
