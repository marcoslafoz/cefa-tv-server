package es.cefa.tv.core.audit.service;

import es.cefa.tv.core.audit.dto.AuditSettingsResponse;
import es.cefa.tv.core.audit.model.AuditSettings;
import es.cefa.tv.core.audit.repository.AuditSettingsRepository;
import es.cefa.tv.core.authorization.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ajustes de retención de la auditoría. */
@Service
@RequiredArgsConstructor
public class AuditSettingsService {

  private final AuditSettingsRepository settings;
  private final AccessService access;

  /**
   * Devuelve los ajustes.
   *
   * @return los ajustes actuales
   */
  @Transactional(readOnly = true)
  public AuditSettingsResponse get() {
    return toResponse(load());
  }

  /**
   * Devuelve los días de retención configurados.
   *
   * @return los días
   */
  @Transactional(readOnly = true)
  public int retentionDays() {
    return load().getRetentionDays();
  }

  /**
   * Cambia los días de retención; solo un administrador puede hacerlo.
   *
   * @param retentionDays nuevos días, entre el mínimo y el máximo permitidos
   * @return los ajustes resultantes
   */
  @Transactional
  public AuditSettingsResponse update(int retentionDays) {
    access.requireAdmin();
    AuditSettings current = load();
    current.setRetentionDays(retentionDays);
    return toResponse(settings.save(current));
  }

  /** Carga la fila única de ajustes. */
  private AuditSettings load() {
    return settings.findById(AuditSettings.SINGLETON_ID).orElseThrow();
  }

  /** Convierte los ajustes en su DTO. */
  private static AuditSettingsResponse toResponse(AuditSettings value) {
    return new AuditSettingsResponse(
        value.getRetentionDays(),
        AuditSettings.MIN_RETENTION_DAYS,
        AuditSettings.MAX_RETENTION_DAYS);
  }
}
