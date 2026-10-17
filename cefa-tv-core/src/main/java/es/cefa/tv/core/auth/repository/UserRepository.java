package es.cefa.tv.core.auth.repository;

import es.cefa.tv.core.auth.model.AuthProvider;
import es.cefa.tv.core.auth.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repositorio de usuarios. */
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Busca un usuario por nombre.
   *
   * @param username nombre de usuario
   * @return el usuario si existe
   */
  Optional<User> findByUsername(String username);

  /**
   * Indica si existe un usuario con ese nombre.
   *
   * @param username nombre de usuario
   * @return {@code true} si existe
   */
  boolean existsByUsernameIgnoreCase(String username);

  /**
   * Busca un usuario por correo sin distinguir mayúsculas.
   *
   * @param email correo
   * @return el usuario si existe
   */
  Optional<User> findByEmailIgnoreCase(String email);

  /**
   * Busca un usuario por el identificador de objeto de Microsoft ya enlazado.
   *
   * @param microsoftObjectId identificador de objeto
   * @return el usuario si existe
   */
  Optional<User> findByMicrosoftObjectId(String microsoftObjectId);

  /**
   * Cuenta los usuarios activos que son administradores, es decir, que pertenecen a algún grupo con
   * acceso total.
   *
   * @return el número de administradores activos
   */
  @Query(
      "select count(u) from User u where u.enabled = true and exists (select 1 from UserGroup g"
          + " where g.allAccess = true and u.id member of g.memberIds)")
  long countActiveAdmins();

  /**
   * Cuenta los administradores activos que pueden entrar con contraseña.
   *
   * @param excluded forma de inicio de sesión que no cuenta
   * @return el número de administradores activos con otra forma de inicio de sesión
   */
  @Query(
      "select count(u) from User u where u.enabled = true and u.authProvider <> :excluded"
          + " and exists (select 1 from UserGroup g where g.allAccess = true"
          + " and u.id member of g.memberIds)")
  long countActiveAdminsWithAuthProviderNot(AuthProvider excluded);
}
