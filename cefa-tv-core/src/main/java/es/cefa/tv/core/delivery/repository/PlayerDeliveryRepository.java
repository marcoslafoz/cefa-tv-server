package es.cefa.tv.core.delivery.repository;

import es.cefa.tv.core.delivery.model.PlayerDelivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de entregas. */
public interface PlayerDeliveryRepository extends JpaRepository<PlayerDelivery, UUID> {

  /**
   * Lista el historial de entregas de un reproductor.
   *
   * @param playerId reproductor
   * @return las entregas, la más reciente primero
   */
  List<PlayerDelivery> findTop100ByPlayerIdOrderByVersionDesc(UUID playerId);

  /**
   * Busca la entrega de una versión.
   *
   * @param playerId reproductor
   * @param version versión
   * @return la entrega si existe
   */
  Optional<PlayerDelivery> findByPlayerIdAndVersion(UUID playerId, long version);

  /**
   * Lista las entregas no cerradas hasta una versión.
   *
   * @param playerId reproductor
   * @param version versión máxima
   * @return las entregas aún en cola o descargando
   */
  List<PlayerDelivery> findByPlayerIdAndVersionLessThanEqualAndStatusIn(
      UUID playerId,
      long version,
      java.util.Collection<es.cefa.tv.core.delivery.model.DeliveryStatus> statuses);
}
