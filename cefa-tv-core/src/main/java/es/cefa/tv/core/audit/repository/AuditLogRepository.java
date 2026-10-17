package es.cefa.tv.core.audit.repository;

import es.cefa.tv.core.audit.model.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Repositorio del registro de auditoría. */
public interface AuditLogRepository
    extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

  /** Recuento de entradas por acción. */
  interface ActionCount {

    /**
     * Devuelve la acción.
     *
     * @return el nombre de la acción
     */
    String getAction();

    /**
     * Devuelve el recuento.
     *
     * @return el número de entradas
     */
    long getTotal();
  }

  /** Recuento de entradas por usuario. */
  interface UserCount {

    /**
     * Devuelve el identificador del usuario.
     *
     * @return el usuario, o {@code null} si fue el sistema o un intento sin identificar
     */
    UUID getUserId();

    /**
     * Devuelve el nombre de usuario.
     *
     * @return el nombre
     */
    String getUsername();

    /**
     * Devuelve el recuento.
     *
     * @return el número de entradas
     */
    long getTotal();
  }

  /** Recuento de entradas por resultado. */
  interface OutcomeCount {

    /**
     * Devuelve el resultado.
     *
     * @return el nombre del resultado
     */
    String getOutcome();

    /**
     * Devuelve el recuento.
     *
     * @return el número de entradas
     */
    long getTotal();
  }

  /** Recuento de entradas por día. */
  interface DayCount {

    /**
     * Devuelve el día en formato {@code yyyy-MM-dd} (UTC).
     *
     * @return el día
     */
    String getDay();

    /**
     * Devuelve el recuento.
     *
     * @return el número de entradas
     */
    long getTotal();
  }

  /**
   * Borra las entradas anteriores a un instante.
   *
   * @param before instante límite
   * @return el número de entradas borradas
   */
  @Modifying
  @Query("delete from AuditLog a where a.at < :before")
  int deleteByAtBefore(Instant before);

  /**
   * Cuenta las entradas de un intervalo.
   *
   * @param from inicio, incluido
   * @param to fin, excluido
   * @return el número de entradas
   */
  @Query("select count(a) from AuditLog a where a.at >= :from and a.at < :to")
  long countBetween(Instant from, Instant to);

  /**
   * Agrupa por acción las entradas de un intervalo, de más a menos frecuentes.
   *
   * @param from inicio, incluido
   * @param to fin, excluido
   * @param pageable límite de resultados
   * @return los recuentos
   */
  @Query(
      "select a.action as action, count(a) as total from AuditLog a"
          + " where a.at >= :from and a.at < :to group by a.action order by count(a) desc")
  List<ActionCount> topActions(Instant from, Instant to, Pageable pageable);

  /**
   * Agrupa por usuario las entradas de un intervalo, de más a menos activos.
   *
   * @param from inicio, incluido
   * @param to fin, excluido
   * @param pageable límite de resultados
   * @return los recuentos
   */
  @Query(
      "select a.userId as userId, a.username as username, count(a) as total from AuditLog a"
          + " where a.at >= :from and a.at < :to group by a.userId, a.username"
          + " order by count(a) desc")
  List<UserCount> topUsers(Instant from, Instant to, Pageable pageable);

  /**
   * Agrupa por resultado las entradas de un intervalo.
   *
   * @param from inicio, incluido
   * @param to fin, excluido
   * @return los recuentos
   */
  @Query(
      "select cast(a.outcome as string) as outcome, count(a) as total from AuditLog a"
          + " where a.at >= :from and a.at < :to group by a.outcome")
  List<OutcomeCount> countByOutcome(Instant from, Instant to);

  /**
   * Agrupa por día (UTC) las entradas de un intervalo.
   *
   * @param from inicio, incluido
   * @param to fin, excluido
   * @return los recuentos por día, del más antiguo al más reciente
   */
  @Query(
      value =
          "select to_char(at at time zone 'UTC', 'YYYY-MM-DD') as day, count(*) as total"
              + " from audit_logs where at >= :from and at < :to group by 1 order by 1",
      nativeQuery = true)
  List<DayCount> countByDay(Instant from, Instant to);

  /**
   * Lista las acciones distintas registradas.
   *
   * @return los nombres de acción ordenados
   */
  @Query("select distinct a.action from AuditLog a order by a.action")
  List<String> distinctActions();
}
