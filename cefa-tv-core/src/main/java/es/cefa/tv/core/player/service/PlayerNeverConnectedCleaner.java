package es.cefa.tv.core.player.service;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.config.CoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Elimina los reproductores emparejados que nunca llegaron a conectarse, normalmente porque el
 * reproductor perdió sus credenciales al emparejarse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerNeverConnectedCleaner {

  private final PlayerRepository players;
  private final PlayerService playerService;
  private final CoreProperties properties;
  private final AuditService audit;

  /** Borra cada hora los reproductores que superan el plazo sin haberse conectado nunca. */
  @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
  public void clean() {
    Instant limit =
        Instant.now().minus(Duration.ofDays(properties.player().neverConnectedDeleteDays()));
    List<Player> orphans = players.findByFirstConnectedAtIsNullAndCreatedAtBefore(limit);
    for (Player player : orphans) {
      playerService.delete(player.getId());
      audit.recordSystem(
          "PLAYER_AUTO_DELETE",
          "PLAYER",
          player.getId().toString(),
          player.getName(),
          AuditOutcome.SUCCESS,
          Map.of("reason", "NEVER_CONNECTED", "createdAt", player.getCreatedAt().toString()));
    }
    if (!orphans.isEmpty()) {
      log.info("Reproductores sin conexión eliminados: {}", orphans.size());
    }
  }
}
