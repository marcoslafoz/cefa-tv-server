package es.cefa.tv.core.audit.snapshot;

import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import es.cefa.tv.core.schedule.model.Schedule;
import es.cefa.tv.core.schedule.model.ScheduleTarget;
import es.cefa.tv.core.schedule.repository.ScheduleRepository;
import es.cefa.tv.core.site.model.Site;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Estado auditable de una programación. */
@Component
@RequiredArgsConstructor
public class ScheduleSnapshot implements AuditSnapshotSource {

  private final ScheduleRepository schedules;
  private final PlaylistRepository playlists;
  private final PlayerRepository players;
  private final PlayerGroupRepository groups;
  private final SiteRepository sites;

  @Override
  public String entityType() {
    return "SCHEDULE";
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(UUID id) {
    return schedules.findById(id).map(this::describe).orElse(null);
  }

  /** Describe una programación con la playlist y los destinos por nombre. */
  private Map<String, Object> describe(Schedule schedule) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("name", schedule.getName());
    state.put(
        "playlist",
        playlists.findById(schedule.getPlaylistId()).map(p -> p.getName()).orElse(null));
    state.put("priority", schedule.getPriority());
    state.put("enabled", schedule.isEnabled());
    state.put("allDay", schedule.isAllDay());
    state.put("startDate", schedule.getStartDate());
    state.put("endDate", schedule.getEndDate());
    state.put("startTime", schedule.getStartTime());
    state.put("endTime", schedule.getEndTime());
    state.put("daysOfWeek", Arrays.asList(schedule.getDaysOfWeek()));
    state.put("targets", targets(schedule));
    return state;
  }

  /** Lista los destinos como texto ordenado. */
  private List<String> targets(Schedule schedule) {
    List<String> names = new ArrayList<>();
    for (ScheduleTarget target : schedule.getTargets()) {
      if (target.getPlayerId() != null) {
        names.add(
            "Reproductor: "
                + players.findById(target.getPlayerId()).map(Player::getName).orElse("?"));
      } else if (target.getGroupId() != null) {
        names.add(
            "Grupo: " + groups.findById(target.getGroupId()).map(PlayerGroup::getName).orElse("?"));
      } else if (target.getSiteId() != null) {
        names.add("Sede: " + sites.findById(target.getSiteId()).map(Site::getName).orElse("?"));
      }
    }
    names.sort(String::compareTo);
    return names;
  }
}
