package es.cefa.tv.core.telemetry.repository;

import es.cefa.tv.core.telemetry.model.PlayerMetric;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Repositorio de muestras de telemetría. */
public interface PlayerMetricRepository extends JpaRepository<PlayerMetric, Long> {

  /**
   * Lista las muestras de un reproductor posteriores a un instante.
   *
   * @param playerId reproductor
   * @param since instante mínimo
   * @return las muestras en orden cronológico
   */
  List<PlayerMetric> findByPlayerIdAndAtAfterOrderByAtAsc(UUID playerId, Instant since);

  /**
   * Borra las muestras anteriores a un instante.
   *
   * @param limit instante de corte
   * @return el número de muestras borradas
   */
  @Modifying
  @Query("delete from PlayerMetric m where m.at < :limit")
  int deleteOlderThan(Instant limit);
}
