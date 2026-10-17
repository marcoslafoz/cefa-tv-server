package es.cefa.tv.core.usergroup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.cefa.tv.core.auth.model.AuthProvider;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.usergroup.service.AdminGuard;
import jakarta.persistence.EntityManager;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Protección frente a quedarse sin administradores o sin ninguno que pueda entrar con contraseña.
 */
class AdminGuardTest {

  private static final String TENANT = "0a1b2c3d-1111-2222-3333-444455556666";

  private UserRepository users;
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    entityManager = mock(EntityManager.class);
  }

  private AdminGuard guard(boolean microsoftEnabled) {
    CoreProperties properties =
        new CoreProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "http://localhost:4000",
            new CoreProperties.Microsoft(
                microsoftEnabled ? TENANT : "", "client", "secret", "", false, ""));
    return new AdminGuard(users, properties, entityManager);
  }

  @Test
  void runsTheChangeAndFlushesBeforeCounting() {
    AtomicBoolean changed = new AtomicBoolean();
    when(users.countActiveAdmins()).thenReturn(2L, 2L);
    when(users.countActiveAdminsWithAuthProviderNot(AuthProvider.MICROSOFT)).thenReturn(2L, 2L);

    guard(false).run(() -> changed.set(true));

    assertThatCode(() -> verify(entityManager).flush()).doesNotThrowAnyException();
    org.assertj.core.api.Assertions.assertThat(changed).isTrue();
  }

  @Test
  void blocksALastAdministratorRemoval() {
    when(users.countActiveAdmins()).thenReturn(1L, 0L);
    when(users.countActiveAdminsWithAuthProviderNot(AuthProvider.MICROSOFT)).thenReturn(1L, 0L);

    assertThatThrownBy(() -> guard(true).run(() -> {}))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("al menos un administrador activo");
  }

  @Test
  void blocksLeavingNoAdministratorWithAPasswordWhileMicrosoftIsNotConfigured() {
    when(users.countActiveAdmins()).thenReturn(2L, 2L);
    when(users.countActiveAdminsWithAuthProviderNot(AuthProvider.MICROSOFT)).thenReturn(1L, 0L);

    assertThatThrownBy(() -> guard(false).run(() -> {}))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("administrador que pueda entrar con contraseña");
  }

  @Test
  void allowsItOnceMicrosoftIsConfigured() {
    when(users.countActiveAdmins()).thenReturn(2L, 2L);
    when(users.countActiveAdminsWithAuthProviderNot(AuthProvider.MICROSOFT)).thenReturn(1L, 0L);

    assertThatCode(() -> guard(true).run(() -> {})).doesNotThrowAnyException();
  }

  @Test
  void allowsItWhenAnotherAdministratorCanStillUseAPassword() {
    when(users.countActiveAdmins()).thenReturn(2L, 2L);
    when(users.countActiveAdminsWithAuthProviderNot(AuthProvider.MICROSOFT)).thenReturn(2L, 1L);

    assertThatCode(() -> guard(false).run(() -> {})).doesNotThrowAnyException();
  }

  @Test
  void doesNotBlockWhenThereWereNoAdministratorsToBeginWith() {
    when(users.countActiveAdmins()).thenReturn(0L, 0L);
    when(users.countActiveAdminsWithAuthProviderNot(AuthProvider.MICROSOFT)).thenReturn(0L, 0L);

    assertThatCode(() -> guard(false).run(() -> {})).doesNotThrowAnyException();
  }
}
