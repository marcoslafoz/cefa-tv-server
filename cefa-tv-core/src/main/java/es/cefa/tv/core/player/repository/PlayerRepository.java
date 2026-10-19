package es.cefa.tv.core.player.repository;

import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerKind;
import es.cefa.tv.core.player.model.PlayerStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Repositorio de reproductores. */
public interface PlayerRepository extends JpaRepository<Player, UUID> {

  /**
   * Busca un reproductor por el resumen de su token.
   *
   * @param hash SHA-256 del token
   * @return el reproductor si existe
   */
  Optional<Player> findByDeviceTokenHash(String hash);

  /**
   * Carga un reproductor bloqueándolo para escritura.
   *
   * @param id identificador
   * @return el reproductor si existe
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Player p where p.id = :id")
  Optional<Player> findByIdForUpdate(UUID id);

  /**
   * Lista los reproductores dados de alta antes de un instante que nunca se han conectado.
   *
   * @param before instante límite de alta
   * @return los reproductores huérfanos
   */
  List<Player> findByFirstConnectedAtIsNullAndCreatedAtBefore(Instant before);

  /**
   * Lista los reproductores con un estado.
   *
   * @param status estado
   * @return los reproductores
   */
  List<Player> findByStatus(PlayerStatus status);

  /**
   * Lista los reproductores de un tipo y estado.
   *
   * @param kind tipo de dispositivo
   * @param status estado administrativo
   * @return los reproductores
   */
  List<Player> findByKindAndStatus(PlayerKind kind, PlayerStatus status);

  /**
   * Lista los reproductores de una sede.
   *
   * @param siteId sede
   * @return los reproductores
   */
  List<Player> findBySiteId(UUID siteId);

  /**
   * Lista los reproductores de un grupo.
   *
   * @param groupId grupo
   * @return los reproductores
   */
  List<Player> findByGroupId(UUID groupId);

  /**
   * Indica si una sede tiene reproductores.
   *
   * @param siteId sede
   * @return {@code true} si tiene alguno
   */
  boolean existsBySiteId(UUID siteId);

  /**
   * Mueve todos los reproductores de una sede a otra.
   *
   * @param from sede de origen
   * @param to sede de destino, o {@code null} para dejarlos sin sede
   * @return el número de reproductores movidos
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Player p set p.siteId = :to where p.siteId = :from")
  int moveSite(UUID from, UUID to);

  /**
   * Cuenta los reproductores de un grupo.
   *
   * @param groupId grupo
   * @return el número de reproductores
   */
  long countByGroupId(UUID groupId);

  /**
   * Cuenta los reproductores vistos después de un instante.
   *
   * @param since instante mínimo del último contacto
   * @return el número de reproductores
   */
  long countByLastSeenAtAfter(Instant since);

  /**
   * Cuenta los reproductores con programación pendiente.
   *
   * @return el número de reproductores
   */
  @Query("select count(p) from Player p where p.desiredVersion > p.appliedVersion")
  long countPending();

  /**
   * Quita un grupo a todos sus reproductores.
   *
   * @param groupId grupo
   */
  @Modifying
  @Query("update Player p set p.groupId = null where p.groupId = :groupId")
  void clearGroup(UUID groupId);

  /**
   * Comprueba si existe un reproductor con ese nombre.
   *
   * @param name nombre
   * @return {@code true} si existe
   */
  boolean existsByName(String name);
}
