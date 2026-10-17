package es.cefa.tv.core.audit.snapshot;

import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import es.cefa.tv.core.site.model.Site;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Estado auditable de un grupo de reproductores. */
@Component
@RequiredArgsConstructor
public class PlayerGroupSnapshot implements AuditSnapshotSource {

  private final PlayerGroupRepository groups;
  private final PlaylistRepository playlists;
  private final SiteRepository sites;

  @Override
  public String entityType() {
    return "PLAYER_GROUP";
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(UUID id) {
    return groups
        .findById(id)
        .map(
            group -> {
              Map<String, Object> state = new LinkedHashMap<>();
              state.put("name", group.getName());
              state.put("icon", group.getIcon());
              state.put("color", group.getColor());
              state.put(
                  "idleLogo", group.getIdleLogo() == null ? null : group.getIdleLogo().name());
              state.put("fallbackPlaylist", playlistName(group.getFallbackPlaylistId()));
              state.put(
                  "site",
                  group.getSiteId() == null
                      ? null
                      : sites.findById(group.getSiteId()).map(Site::getName).orElse(null));
              return state;
            })
        .orElse(null);
  }

  /** Obtiene el nombre de la playlist de reposo, si hay. */
  private String playlistName(UUID playlistId) {
    return playlistId == null
        ? null
        : playlists.findById(playlistId).map(playlist -> playlist.getName()).orElse(null);
  }
}
