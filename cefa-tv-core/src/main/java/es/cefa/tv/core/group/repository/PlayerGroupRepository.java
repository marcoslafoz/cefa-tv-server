package es.cefa.tv.core.group.repository;

import es.cefa.tv.core.group.model.PlayerGroup;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Repositorio de grupos de reproductores. */
public interface PlayerGroupRepository extends JpaRepository<PlayerGroup, UUID> {

  /**
   * Indica si existe un grupo con ese nombre.
   *
   * @param name nombre
   * @return {@code true} si existe
   */
  boolean existsByName(String name);

  /**
   * Indica si una sede tiene grupos asignados.
   *
   * @param siteId sede
   * @return {@code true} si tiene alguno
   */
  boolean existsBySiteId(UUID siteId);

  /**
   * Lista los grupos asignados a una sede.
   *
   * @param siteId sede
   * @return los grupos
   */
  List<PlayerGroup> findBySiteId(UUID siteId);

  /**
   * Mueve todos los grupos de una sede a otra.
   *
   * @param from sede de origen
   * @param to sede de destino, o {@code null} para dejarlos sin sede
   * @return el número de grupos movidos
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update PlayerGroup g set g.siteId = :to where g.siteId = :from")
  int moveSite(UUID from, UUID to);
}
