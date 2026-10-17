package es.cefa.tv.core.usergroup.repository;

import es.cefa.tv.core.usergroup.model.UserGroup;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repositorio de grupos de usuarios. */
public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {

  /**
   * Indica si existe un grupo con ese nombre.
   *
   * @param name nombre
   * @return {@code true} si existe
   */
  boolean existsByNameIgnoreCase(String name);

  /**
   * Lista los identificadores de los grupos a los que pertenece un usuario.
   *
   * @param userId usuario
   * @return los identificadores de grupo
   */
  @Query("select g.id from UserGroup g where :userId member of g.memberIds")
  List<UUID> findIdsByMember(UUID userId);

  /**
   * Lista los grupos a los que pertenece un usuario.
   *
   * @param userId usuario
   * @return los grupos
   */
  @Query("select g from UserGroup g where :userId member of g.memberIds")
  List<UserGroup> findByMember(UUID userId);
}
