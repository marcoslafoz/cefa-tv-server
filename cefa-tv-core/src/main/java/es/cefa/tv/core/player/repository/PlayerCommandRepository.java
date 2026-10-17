package es.cefa.tv.core.player.repository;

import es.cefa.tv.core.player.model.PlayerCommand;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de órdenes pendientes. */
public interface PlayerCommandRepository extends JpaRepository<PlayerCommand, UUID> {

  /**
   * Lista las órdenes sin entregar de un reproductor.
   *
   * @param playerId reproductor
   * @return las órdenes en orden de creación
   */
  List<PlayerCommand> findByPlayerIdAndDeliveredAtIsNullOrderByCreatedAtAsc(UUID playerId);
}
