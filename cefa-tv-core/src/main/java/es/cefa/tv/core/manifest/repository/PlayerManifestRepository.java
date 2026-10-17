package es.cefa.tv.core.manifest.repository;

import es.cefa.tv.core.manifest.model.PlayerManifest;
import es.cefa.tv.core.manifest.model.PlayerManifestId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de manifests. */
public interface PlayerManifestRepository extends JpaRepository<PlayerManifest, PlayerManifestId> {

  /**
   * Devuelve la última versión de un reproductor.
   *
   * @param playerId reproductor
   * @return el manifest más reciente
   */
  Optional<PlayerManifest> findFirstByPlayerIdOrderByVersionDesc(UUID playerId);
}
