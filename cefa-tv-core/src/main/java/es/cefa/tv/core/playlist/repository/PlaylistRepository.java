package es.cefa.tv.core.playlist.repository;

import es.cefa.tv.core.playlist.model.Playlist;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de playlists. */
public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

  /**
   * Lista las playlists visibles, más recientes primero.
   *
   * @return las playlists no ocultas
   */
  List<Playlist> findByHiddenFalseOrderByUpdatedAtDesc();

  /**
   * Lista todas las playlists, incluidas las ocultas, más recientes primero.
   *
   * @return todas las playlists
   */
  List<Playlist> findAllByOrderByUpdatedAtDesc();
}
