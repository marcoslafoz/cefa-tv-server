package es.cefa.tv.core.group.service;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.site.model.Site;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Mantiene la regla de consistencia entre grupos y sedes: si un grupo tiene sede, la sede de todos
 * sus reproductores es la del grupo.
 */
@Service
@RequiredArgsConstructor
public class GroupSiteService {

  private final PlayerGroupRepository groups;
  private final PlayerRepository players;
  private final SiteRepository sites;
  private final AuditService audit;
  private final AccessService access;
  private final AuthenticatedUserLoader loader;

  /**
   * Devuelve la sede de un grupo.
   *
   * @param groupId grupo, o {@code null}
   * @return la sede del grupo, o {@code null} si no tiene o no hay grupo
   */
  public UUID siteOf(UUID groupId) {
    return groupId == null
        ? null
        : groups.findById(groupId).map(PlayerGroup::getSiteId).orElse(null);
  }

  /**
   * Pasa todos los reproductores del grupo a la sede del grupo y audita cada cambio. No hace nada
   * si el grupo no tiene sede.
   *
   * @param group grupo ya guardado con su sede
   * @return el número de reproductores cuya sede cambió
   */
  public int propagate(PlayerGroup group) {
    if (group.getSiteId() == null) {
      return 0;
    }
    List<Player> changed =
        players.findByGroupId(group.getId()).stream()
            .filter(player -> !group.getSiteId().equals(player.getSiteId()))
            .toList();
    for (Player player : changed) {
      String before = siteName(player.getSiteId());
      player.setSiteId(group.getSiteId());
      recordPlayerAssignment(player, before, group.getName());
    }
    players.saveAll(changed);
    loader.invalidateAllAfterCommit();
    return changed.size();
  }

  /**
   * Da a un reproductor recién creado la sede de su grupo, si el grupo la tiene, y lo audita.
   *
   * @param player reproductor aún sin guardar, con su grupo ya asignado
   */
  public void inheritOnCreate(Player player) {
    if (player.getGroupId() == null) {
      return;
    }
    groups
        .findById(player.getGroupId())
        .filter(group -> group.getSiteId() != null)
        .ifPresent(
            group -> {
              player.setSiteId(group.getSiteId());
              recordPlayerAssignment(player, null, group.getName());
            });
  }

  /**
   * Audita el cambio de sede de un grupo con el número de reproductores afectados.
   *
   * @param group grupo
   * @param previousSite sede anterior, o {@code null}
   * @param affectedPlayers reproductores cuya sede cambió
   */
  public void recordGroupAssignment(PlayerGroup group, UUID previousSite, int affectedPlayers) {
    audit.recordUser(
        access.current(),
        AuditClient.current(),
        "GROUP_SITE_ASSIGN",
        "PLAYER_GROUP",
        group.getId().toString(),
        group.getName(),
        AuditOutcome.SUCCESS,
        Map.of(
            "affectedPlayers",
            affectedPlayers,
            "changes",
            List.of(change(siteName(previousSite), siteName(group.getSiteId())))));
  }

  /**
   * Audita que un reproductor ha recibido la sede de su grupo.
   *
   * @param player reproductor
   * @param before nombre de su sede anterior, o {@code null}
   * @param groupName nombre del grupo del que hereda
   */
  public void recordPlayerAssignment(Player player, String before, String groupName) {
    audit.recordUser(
        access.current(),
        AuditClient.current(),
        "PLAYER_SITE_ASSIGN",
        "PLAYER",
        player.getId().toString(),
        player.getName(),
        AuditOutcome.SUCCESS,
        Map.of(
            "changes",
            List.of(change(before, siteName(player.getSiteId()))),
            "inheritedFromGroup",
            groupName));
  }

  /** Nombre de una sede, o {@code null} si no hay sede. */
  public String siteName(UUID siteId) {
    return siteId == null ? null : sites.findById(siteId).map(Site::getName).orElse(null);
  }

  /** Cambio de la sede con el formato de la auditoría. */
  private static Map<String, Object> change(String before, String after) {
    Map<String, Object> change = new LinkedHashMap<>();
    change.put("field", "site");
    change.put("before", before);
    change.put("after", after);
    return change;
  }
}
