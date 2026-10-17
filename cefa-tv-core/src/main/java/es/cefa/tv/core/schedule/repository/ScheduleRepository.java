package es.cefa.tv.core.schedule.repository;

import es.cefa.tv.core.schedule.model.Schedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repositorio de programaciones. */
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

  /**
   * Indica si una playlist está en alguna programación.
   *
   * @param playlistId playlist
   * @return {@code true} si se usa
   */
  boolean existsByPlaylistId(UUID playlistId);

  /**
   * Cuenta las programaciones de una playlist.
   *
   * @param playlistId playlist
   * @return el número de programaciones
   */
  long countByPlaylistId(UUID playlistId);

  /**
   * Busca las programaciones habilitadas que afectan a un reproductor, directamente, por su grupo o
   * por su sede.
   *
   * @param playerId reproductor
   * @param groupId grupo del reproductor, o un UUID nulo ({@code 0000...}) si no tiene
   * @param siteId sede del reproductor, o un UUID nulo si no tiene
   * @return las programaciones aplicables
   */
  @Query(
      "select distinct s from Schedule s join s.targets t where s.enabled = true"
          + " and (t.playerId = :playerId or t.groupId = :groupId or t.siteId = :siteId)")
  List<Schedule> findEnabledForPlayer(UUID playerId, UUID groupId, UUID siteId);

  /**
   * Carga las programaciones habilitadas con sus destinos en una sola consulta.
   *
   * @return las programaciones habilitadas
   */
  @Query("select distinct s from Schedule s left join fetch s.targets where s.enabled = true")
  List<Schedule> findAllEnabledWithTargets();

  /**
   * Indica si alguna programación apunta a una sede.
   *
   * @param siteId sede
   * @return {@code true} si es destino de alguna programación
   */
  @Query("select count(t) > 0 from ScheduleTarget t where t.siteId = :siteId")
  boolean existsTargetForSite(UUID siteId);

  /**
   * Busca todas las programaciones que afectan a un reproductor, estén o no habilitadas.
   *
   * @param playerId reproductor
   * @param groupId grupo del reproductor, o un UUID nulo si no tiene
   * @param siteId sede del reproductor, o un UUID nulo si no tiene
   * @return las programaciones aplicables
   */
  @Query(
      "select distinct s from Schedule s join s.targets t"
          + " where t.playerId = :playerId or t.groupId = :groupId or t.siteId = :siteId")
  List<Schedule> findAllForPlayer(UUID playerId, UUID groupId, UUID siteId);
}
