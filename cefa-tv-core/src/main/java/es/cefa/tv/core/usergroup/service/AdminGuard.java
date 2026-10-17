package es.cefa.tv.core.usergroup.service;

import es.cefa.tv.core.auth.model.AuthProvider;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Impide dejar el sistema sin administradores: ejecuta un cambio y, si con él no queda ningún
 * administrador activo (o ninguno que pueda entrar con contraseña mientras Microsoft no esté
 * configurado), lanza un error y la transacción se deshace.
 */
@Component
@RequiredArgsConstructor
public class AdminGuard {

  private final UserRepository users;
  private final CoreProperties properties;
  private final EntityManager entityManager;

  /**
   * Aplica un cambio comprobando que sigue habiendo administradores.
   *
   * @param change cambio que puede afectar a usuarios, grupos o membresías
   */
  public void run(Runnable change) {
    long admins = users.countActiveAdmins();
    long passwordAdmins = users.countActiveAdminsWithAuthProviderNot(AuthProvider.MICROSOFT);
    change.run();
    entityManager.flush();
    if (admins > 0 && users.countActiveAdmins() == 0) {
      throw ApiException.conflict(
          "LAST_ADMIN", "Debe quedar al menos un administrador activo en el sistema");
    }
    if (passwordAdmins > 0
        && !properties.microsoft().enabled()
        && users.countActiveAdminsWithAuthProviderNot(AuthProvider.MICROSOFT) == 0) {
      throw ApiException.conflict(
          "LAST_PASSWORD_ADMIN",
          "Debe quedar al menos un administrador que pueda entrar con contraseña");
    }
  }
}
