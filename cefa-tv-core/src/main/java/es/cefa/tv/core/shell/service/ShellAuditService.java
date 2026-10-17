package es.cefa.tv.core.shell.service;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.audit.service.AuditEvent;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shell.dto.ShellAuditResponse;
import es.cefa.tv.core.shell.model.ShellAudit;
import es.cefa.tv.core.shell.model.ShellCloseReason;
import es.cefa.tv.core.shell.repository.ShellAuditRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistencia de la auditoría de sesiones de terminal remota. */
@Service
@RequiredArgsConstructor
public class ShellAuditService {

  private final ShellAuditRepository audits;
  private final PlayerRepository players;
  private final AuditService audit;

  /**
   * Registra la apertura de una sesión.
   *
   * @param playerId reproductor
   * @param playerName nombre del reproductor
   * @param user usuario que abre la sesión
   * @param client origen de la conexión
   * @return el identificador de la auditoría
   */
  @Transactional
  public UUID open(UUID playerId, String playerName, AuthenticatedUser user, AuditClient client) {
    ShellAudit audit = new ShellAudit();
    audit.setId(UUID.randomUUID());
    audit.setPlayerId(playerId);
    audit.setPlayerName(playerName);
    audit.setUserId(user.id());
    audit.setUsername(user.username());
    audit.setOpenedAt(Instant.now());
    audits.save(audit);
    this.audit.recordUser(
        user,
        client,
        "PLAYER_SHELL_OPEN",
        "PLAYER",
        playerId.toString(),
        playerName,
        AuditOutcome.SUCCESS,
        Map.of("shellAuditId", audit.getId().toString()));
    return audit.getId();
  }

  /**
   * Registra el cierre de una sesión.
   *
   * @param auditId auditoría de la sesión
   * @param bytesIn bytes enviados por el usuario
   * @param bytesOut bytes recibidos del reproductor
   * @param reason motivo del cierre
   */
  @Transactional
  public void close(UUID auditId, long bytesIn, long bytesOut, ShellCloseReason reason) {
    audits
        .findById(auditId)
        .ifPresent(
            audit -> {
              audit.setClosedAt(Instant.now());
              audit.setBytesIn(bytesIn);
              audit.setBytesOut(bytesOut);
              audit.setCloseReason(reason.name());
              this.audit.record(
                  new AuditEvent(
                      audit.getUserId(),
                      audit.getUsername(),
                      "PLAYER_SHELL_CLOSE",
                      "PLAYER",
                      audit.getPlayerId().toString(),
                      audit.getPlayerName(),
                      AuditOutcome.SUCCESS,
                      Map.of(
                          "shellAuditId",
                          audit.getId().toString(),
                          "reason",
                          reason.name(),
                          "bytesIn",
                          bytesIn,
                          "bytesOut",
                          bytesOut),
                      null,
                      null));
            });
  }

  /**
   * Lista las últimas sesiones de un reproductor.
   *
   * @param playerId reproductor
   * @return las sesiones, de la más reciente a la más antigua
   */
  @Transactional(readOnly = true)
  public List<ShellAuditResponse> list(UUID playerId) {
    if (!players.existsById(playerId)) {
      throw ApiException.notFound("Reproductor");
    }
    return audits.findTop100ByPlayerIdOrderByOpenedAtDesc(playerId).stream()
        .map(
            audit ->
                new ShellAuditResponse(
                    audit.getId(),
                    audit.getPlayerId(),
                    audit.getPlayerName(),
                    audit.getUsername(),
                    audit.getOpenedAt(),
                    audit.getClosedAt(),
                    audit.getBytesIn(),
                    audit.getBytesOut(),
                    audit.getCloseReason()))
        .toList();
  }

  /** Cierra las sesiones que quedaron abiertas por un reinicio del servidor. */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void closeDanglingSessions() {
    Instant now = Instant.now();
    audits
        .findByClosedAtIsNull()
        .forEach(
            audit -> {
              audit.setClosedAt(now);
              audit.setCloseReason(ShellCloseReason.SERVER_RESTART.name());
            });
  }
}
