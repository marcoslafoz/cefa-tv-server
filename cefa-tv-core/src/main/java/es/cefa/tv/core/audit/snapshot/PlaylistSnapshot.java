package es.cefa.tv.core.audit.snapshot;

import es.cefa.tv.core.playlist.model.Playlist;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Estado auditable de una playlist. */
@Component
@RequiredArgsConstructor
public class PlaylistSnapshot implements AuditSnapshotSource {

  private final PlaylistRepository playlists;

  @Override
  public String entityType() {
    return "PLAYLIST";
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(UUID id) {
    return playlists.findById(id).map(PlaylistSnapshot::describe).orElse(null);
  }

  /** Describe una playlist con sus elementos en orden. */
  private static Map<String, Object> describe(Playlist playlist) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("name", playlist.getName());
    state.put("description", playlist.getDescription());
    state.put("hidden", playlist.isHidden());
    state.put(
        "items",
        playlist.getItems().stream()
            .map(item -> item.getAsset().getName() + " (" + item.getDurationSec() + " s)")
            .toList());
    return state;
  }
}
