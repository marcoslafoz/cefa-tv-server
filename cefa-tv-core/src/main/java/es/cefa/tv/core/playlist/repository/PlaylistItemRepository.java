package es.cefa.tv.core.playlist.repository;

import es.cefa.tv.core.playlist.model.PlaylistItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de elementos de playlist. */
public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, UUID> {

  /**
   * Indica si un medio está en alguna playlist.
   *
   * @param assetId identificador del medio
   * @return {@code true} si se usa
   */
  boolean existsByAssetId(UUID assetId);

  /**
   * Lista los elementos de una playlist en orden.
   *
   * @param playlistId identificador de la playlist
   * @return los elementos ordenados por posición
   */
  List<PlaylistItem> findByPlaylistIdOrderByPositionAsc(UUID playlistId);
}
