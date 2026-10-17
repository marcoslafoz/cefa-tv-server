package es.cefa.tv.core.usergroup.repository;

import es.cefa.tv.core.usergroup.model.AccessGrant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

/** Repositorio de concesiones de acceso. */
public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

  /**
   * Lista las concesiones de un grupo de usuarios.
   *
   * @param userGroupId grupo de usuarios
   * @return las concesiones
   */
  List<AccessGrant> findByUserGroupId(UUID userGroupId);

  /**
   * Lista las concesiones de varios grupos de usuarios.
   *
   * @param userGroupIds grupos de usuarios
   * @return las concesiones
   */
  List<AccessGrant> findByUserGroupIdIn(Collection<UUID> userGroupIds);

  /**
   * Borra las concesiones de un grupo de usuarios.
   *
   * @param userGroupId grupo de usuarios
   */
  @Modifying
  void deleteByUserGroupId(UUID userGroupId);
}
