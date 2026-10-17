package es.cefa.tv.core.schedule.service;

import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.service.PlayerTimezoneResolver;
import es.cefa.tv.core.schedule.model.Schedule;
import es.cefa.tv.core.schedule.model.ScheduleTarget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Índice de reproductores y zonas horarias, cargado una vez, para saber en qué zonas horarias se
 * evalúa cada programación según los reproductores a los que llega.
 */
final class ScheduleZones {

  private final Map<UUID, Player> byId = new HashMap<>();
  private final Map<UUID, List<Player>> byGroup = new HashMap<>();
  private final Map<UUID, List<Player>> bySite = new HashMap<>();
  private final Map<UUID, String> siteZones;
  private final PlayerTimezoneResolver resolver;

  /**
   * Construye el índice.
   *
   * @param players todos los reproductores
   * @param siteZones zonas horarias de las sedes por identificador
   * @param resolver resolvedor de la zona horaria de un reproductor
   */
  ScheduleZones(
      Collection<Player> players, Map<UUID, String> siteZones, PlayerTimezoneResolver resolver) {
    this.siteZones = siteZones;
    this.resolver = resolver;
    for (Player player : players) {
      byId.put(player.getId(), player);
      if (player.getGroupId() != null) {
        byGroup.computeIfAbsent(player.getGroupId(), id -> new ArrayList<>()).add(player);
      }
      if (player.getSiteId() != null) {
        bySite.computeIfAbsent(player.getSiteId(), id -> new ArrayList<>()).add(player);
      }
    }
  }

  /**
   * Zonas horarias distintas de los reproductores a los que llega una programación, ordenadas.
   *
   * @param schedule programación
   * @return los identificadores de zona, vacío si no llega a ningún reproductor
   */
  List<String> zonesOf(Schedule schedule) {
    TreeSet<String> zones = new TreeSet<>();
    for (ScheduleTarget target : schedule.getTargets()) {
      playersOf(target).forEach(player -> zones.add(resolver.timezoneOf(player, siteZones)));
    }
    return List.copyOf(zones);
  }

  /** Reproductores a los que apunta un destino. */
  private List<Player> playersOf(ScheduleTarget target) {
    if (target.getPlayerId() != null) {
      Player player = byId.get(target.getPlayerId());
      return player == null ? List.of() : List.of(player);
    }
    if (target.getGroupId() != null) {
      return byGroup.getOrDefault(target.getGroupId(), List.of());
    }
    return bySite.getOrDefault(target.getSiteId(), List.of());
  }
}
