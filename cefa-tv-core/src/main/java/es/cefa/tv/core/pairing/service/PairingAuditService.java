package es.cefa.tv.core.pairing.service;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.pairing.model.PairingAudit;
import es.cefa.tv.core.pairing.repository.PairingAuditRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra los intentos de emparejamiento en una transacción propia para que sobrevivan al error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PairingAuditService {

  private final PairingAuditRepository audits;

  /**
   * Registra un intento.
   *
   * @param user usuario que lo realizó
   * @param outcome resultado
   * @param playerId reproductor emparejado, si lo hubo
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuthenticatedUser user, PairingAudit.Outcome outcome, UUID playerId) {
    PairingAudit audit = new PairingAudit();
    audit.setId(UUID.randomUUID());
    audit.setUserId(user.id());
    audit.setUsername(user.username());
    audit.setOutcome(outcome);
    audit.setPlayerId(playerId);
    audit.setCreatedAt(Instant.now());
    audits.save(audit);
    log.info("Emparejamiento de {}: {}", user.username(), outcome);
  }
}
