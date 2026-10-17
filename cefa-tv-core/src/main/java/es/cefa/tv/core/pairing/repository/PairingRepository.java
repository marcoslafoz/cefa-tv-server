package es.cefa.tv.core.pairing.repository;

import es.cefa.tv.core.pairing.model.Pairing;
import es.cefa.tv.core.pairing.model.PairingStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Repositorio de emparejamientos. */
public interface PairingRepository extends JpaRepository<Pairing, UUID> {

  /**
   * Lista los emparejamientos pendientes y vigentes.
   *
   * @param status estado buscado
   * @param now instante de evaluación
   * @return los emparejamientos, los más recientes primero
   */
  List<Pairing> findByStatusAndExpiresAtAfterOrderByCreatedAtDesc(
      PairingStatus status, Instant now);

  /**
   * Indica si ya hay un emparejamiento con ese código y estado.
   *
   * @param code código
   * @param status estado
   * @return {@code true} si existe
   */
  boolean existsByCodeAndStatus(String code, PairingStatus status);

  /**
   * Carga un emparejamiento bloqueándolo para escritura.
   *
   * @param id identificador
   * @return el emparejamiento si existe
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Pairing p where p.id = :id")
  Optional<Pairing> findByIdForUpdate(UUID id);

  /**
   * Lista los pendientes de un origen para limitar su número.
   *
   * @param ip dirección de origen
   * @param status estado buscado
   * @return los emparejamientos del origen, los más antiguos primero
   */
  List<Pairing> findByIpAndStatusOrderByCreatedAtAsc(String ip, PairingStatus status);

  /**
   * Marca como caducados los pendientes vencidos.
   *
   * @param now instante de evaluación
   * @return el número de filas afectadas
   */
  @Modifying
  @Query(
      "update Pairing p set p.status = es.cefa.tv.core.pairing.model.PairingStatus.EXPIRED"
          + " where p.status = es.cefa.tv.core.pairing.model.PairingStatus.PENDING"
          + " and p.expiresAt <= :now")
  int expirePending(Instant now);

  /**
   * Marca como consumido el emparejamiento aprobado de un reproductor.
   *
   * @param playerId reproductor
   * @return el número de filas afectadas
   */
  @Modifying
  @Query(
      "update Pairing p set p.status = es.cefa.tv.core.pairing.model.PairingStatus.CONSUMED"
          + " where p.playerId = :playerId"
          + " and p.status = es.cefa.tv.core.pairing.model.PairingStatus.APPROVED")
  int consumeByPlayerId(UUID playerId);

  /**
   * Borra los emparejamientos anteriores a un instante: los aprobados según su fecha de aprobación,
   * para que el reproductor tenga todo el plazo para recoger sus credenciales, y el resto según su
   * creación.
   *
   * @param before instante límite de creación
   * @return el número de filas borradas
   */
  @Modifying
  @Query(
      "delete from Pairing p where (p.status = es.cefa.tv.core.pairing.model.PairingStatus.APPROVED"
          + " and coalesce(p.approvedAt, p.createdAt) < :before)"
          + " or (p.status <> es.cefa.tv.core.pairing.model.PairingStatus.APPROVED"
          + " and p.createdAt < :before)")
  int deleteCreatedBefore(Instant before);
}
