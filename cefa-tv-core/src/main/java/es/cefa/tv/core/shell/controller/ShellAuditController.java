package es.cefa.tv.core.shell.controller;

import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.annotation.RequiresScope;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.shell.dto.ShellAuditResponse;
import es.cefa.tv.core.shell.service.ShellAuditService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la auditoría de terminales remotas, reservada a quien tenga el permiso de terminal.
 */
@RestController
@RequestMapping("/api/v1/players/{id}/shell-audits")
@RequiredArgsConstructor
@RequiresPermission(Permission.PLAYER_TERMINAL)
@RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.OPERATE)
public class ShellAuditController {

  private final ShellAuditService auditService;

  /**
   * Lista las últimas sesiones de terminal de un reproductor.
   *
   * @param id reproductor
   * @return las sesiones auditadas
   */
  @GetMapping
  public List<ShellAuditResponse> list(@PathVariable UUID id) {
    return auditService.list(id);
  }
}
