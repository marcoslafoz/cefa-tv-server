package es.cefa.tv.core.audit.repository;

import es.cefa.tv.core.audit.model.AuditSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de los ajustes de auditoría. */
public interface AuditSettingsRepository extends JpaRepository<AuditSettings, UUID> {}
