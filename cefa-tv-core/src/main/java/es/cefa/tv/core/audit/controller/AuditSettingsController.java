package es.cefa.tv.core.audit.controller;

import es.cefa.tv.core.audit.dto.AuditSettingsRequest;
import es.cefa.tv.core.audit.dto.AuditSettingsResponse;
import es.cefa.tv.core.audit.service.AuditSettingsService;
import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ajustes de retención de la auditoría. */
@RestController
@RequestMapping("/api/v1/settings/audit")
@RequiredArgsConstructor
@RequiresPermission(Permission.AUDIT_VIEW)
public class AuditSettingsController {

  private final AuditSettingsService settings;

  /**
   * Devuelve los ajustes.
   *
   * @return los ajustes de retención
   */
  @GetMapping
  public AuditSettingsResponse get() {
    return settings.get();
  }

  /**
   * Cambia la retención; solo un administrador.
   *
   * @param request nuevos días de retención
   * @return los ajustes resultantes
   */
  @PutMapping
  public AuditSettingsResponse update(@Valid @RequestBody AuditSettingsRequest request) {
    return settings.update(request.retentionDays());
  }
}
