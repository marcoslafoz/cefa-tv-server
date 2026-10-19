package es.cefa.tv.core.aracast.repository;

import es.cefa.tv.core.aracast.model.AracastPlayer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los datos de conexión de los reproductores Aracast. */
public interface AracastPlayerRepository extends JpaRepository<AracastPlayer, UUID> {

  /**
   * Busca un Aracast por su dirección.
   *
   * @param host IP o nombre
   * @return el Aracast, si existe
   */
  Optional<AracastPlayer> findByHostIgnoreCase(String host);
}
