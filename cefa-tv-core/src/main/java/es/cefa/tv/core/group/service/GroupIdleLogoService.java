package es.cefa.tv.core.group.service;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.player.model.IdleLogo;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerCommandService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Propaga a los reproductores el logo de la pantalla sin programación de su grupo y audita los
 * cambios.
 */
@Service
@RequiredArgsConstructor
public class GroupIdleLogoService {

  private final PlayerRepository players;
  private final PlayerCommandService commandService;
  private final AuditService audit;
  private final AccessService access;

  /**
   * Envía la configuración nueva a los reproductores del grupo que heredan su logo, es decir, los
   * que no tienen uno propio.
   *
   * @param group grupo ya guardado
   * @return el número de reproductores afectados
   */
  public int push(PlayerGroup group) {
    List<Player> inheriting = inheriting(group.getId());
    inheriting.forEach(commandService::sendConfig);
    return inheriting.size();
  }

  /**
   * Envía la configuración a reproductores concretos tras haber cambiado su grupo.
   *
   * @param playerIds reproductores
   */
  public void pushTo(List<UUID> playerIds) {
    players.findAllById(playerIds).forEach(commandService::sendConfig);
  }

  /**
   * Identificadores de los reproductores de un grupo que heredan su logo.
   *
   * @param groupId grupo
   * @return los identificadores
   */
  public List<UUID> inheritingIds(UUID groupId) {
    return inheriting(groupId).stream().map(Player::getId).toList();
  }

  /**
   * Audita el cambio de logo de un grupo con el número de reproductores afectados.
   *
   * @param group grupo
   * @param before logo anterior, o {@code null}
   * @param affectedPlayers reproductores que heredan el logo del grupo
   */
  public void record(PlayerGroup group, IdleLogo before, int affectedPlayers) {
    Map<String, Object> change = new LinkedHashMap<>();
    change.put("field", "idleLogo");
    change.put("before", before == null ? null : before.name());
    change.put("after", group.getIdleLogo() == null ? null : group.getIdleLogo().name());
    audit.recordUser(
        access.current(),
        AuditClient.current(),
        "GROUP_IDLE_LOGO_CHANGE",
        "PLAYER_GROUP",
        group.getId().toString(),
        group.getName(),
        AuditOutcome.SUCCESS,
        Map.of("affectedPlayers", affectedPlayers, "changes", List.of(change)));
  }

  /** Reproductores de un grupo sin logo propio. */
  private List<Player> inheriting(UUID groupId) {
    return players.findByGroupId(groupId).stream()
        .filter(player -> player.getIdleLogo() == null)
        .toList();
  }
}
