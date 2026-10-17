package es.cefa.tv.core.pairing.repository;

import es.cefa.tv.core.pairing.model.PairingAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de la auditoría de emparejamientos. */
public interface PairingAuditRepository extends JpaRepository<PairingAudit, UUID> {}
