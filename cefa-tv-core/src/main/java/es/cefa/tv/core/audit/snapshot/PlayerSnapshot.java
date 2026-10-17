package es.cefa.tv.core.audit.snapshot;

import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.playlist.model.Playlist;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import es.cefa.tv.core.site.model.Site;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Estado auditable de un reproductor. */
@Component
@RequiredArgsConstructor
public class PlayerSnapshot implements AuditSnapshotSource {

  private final PlayerRepository players;
  private final PlayerGroupRepository groups;
  private final PlaylistRepository playlists;
  private final SiteRepository sites;

  @Override
  public String entityType() {
    return "PLAYER";
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(UUID id) {
    return players.findById(id).map(this::describe).orElse(null);
  }

  /** Describe un reproductor con los campos que un usuario puede cambiar. */
  private Map<String, Object> describe(Player player) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("name", player.getName());
    state.put("description", player.getDescription());
    state.put(
        "site",
        player.getSiteId() == null
            ? null
            : sites.findById(player.getSiteId()).map(Site::getName).orElse(null));
    state.put(
        "group",
        player.getGroupId() == null
            ? null
            : groups.findById(player.getGroupId()).map(PlayerGroup::getName).orElse(null));
    state.put("orientation", player.getOrientation());
    state.put("preferredOutput", player.getPreferredOutput());
    state.put("idleLogo", player.getIdleLogo() == null ? null : player.getIdleLogo().name());
    state.put("status", player.getStatus().name());
    state.put(
        "fallbackPlaylist",
        player.getFallbackPlaylistId() == null
            ? null
            : playlists
                .findById(player.getFallbackPlaylistId())
                .map(Playlist::getName)
                .orElse(null));
    return state;
  }
}
