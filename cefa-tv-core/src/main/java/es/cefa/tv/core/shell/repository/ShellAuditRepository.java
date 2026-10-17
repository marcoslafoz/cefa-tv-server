package es.cefa.tv.core.shell.repository;

import es.cefa.tv.core.shell.model.ShellAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de auditorías de terminal remota. */
public interface ShellAuditRepository extends JpaRepository<ShellAudit, UUID> {

  /**
   * Lista las últimas sesiones de un reproductor.
   *
   * @param playerId reproductor
   * @return hasta 100 sesiones, de la más reciente a la más antigua
   */
  List<ShellAudit> findTop100ByPlayerIdOrderByOpenedAtDesc(UUID playerId);

  /**
   * Lista las sesiones que no llegaron a cerrarse.
   *
   * @return las sesiones abiertas
   */
  List<ShellAudit> findByClosedAtIsNull();
}
