package es.cefa.tv.core.microsoft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.cefa.tv.core.auth.model.AuthProvider;
import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.microsoft.dto.MicrosoftIdentity;
import es.cefa.tv.core.microsoft.dto.MicrosoftLoginResult;
import es.cefa.tv.core.microsoft.service.MicrosoftUserResolver;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.usergroup.model.UserGroup;
import es.cefa.tv.core.usergroup.repository.UserGroupRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Pruebas del enlace de una identidad de Microsoft con el usuario local. */
class MicrosoftUserResolverTest {

  private static final String OID = "11111111-2222-3333-4444-555555555555";

  private UserRepository users;
  private UserGroupRepository userGroups;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    userGroups = mock(UserGroupRepository.class);
  }

  private MicrosoftUserResolver resolver(boolean autoProvision) {
    return resolver(autoProvision, "");
  }

  private MicrosoftUserResolver resolver(boolean autoProvision, String defaultGroupId) {
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
                "tenant", "client", "secret", "", autoProvision, defaultGroupId));
    return new MicrosoftUserResolver(users, properties, userGroups);
  }

  private static User user(AuthProvider provider, boolean enabled) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername("ana");
    user.setEmail("ana@cefa.es");
    user.setAuthProvider(provider);
    user.setEnabled(enabled);
    return user;
  }

  @Test
  void usesTheUserAlreadyLinkedByObjectIdEvenIfTheEmailChanged() {
    User linked = user(AuthProvider.MICROSOFT, true);
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.of(linked));

    MicrosoftLoginResult result =
        resolver(false).resolve(new MicrosoftIdentity("otro@cefa.es", OID, "Ana"));

    assertThat(result.user()).isSameAs(linked);
  }

  @Test
  void linksByEmailIgnoringCaseAndStoresTheObjectId() {
    User local = user(AuthProvider.HYBRID, true);
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("ana@cefa.es")).thenReturn(Optional.of(local));

    MicrosoftLoginResult result =
        resolver(false).resolve(new MicrosoftIdentity("ana@cefa.es", OID, "Ana"));

    assertThat(result.isAccepted()).isTrue();
    assertThat(local.getMicrosoftObjectId()).isEqualTo(OID);
  }

  @Test
  void rejectsAnEmailAlreadyBoundToAnotherMicrosoftAccount() {
    User local = user(AuthProvider.HYBRID, true);
    local.setMicrosoftObjectId("99999999-0000-0000-0000-000000000000");
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("ana@cefa.es")).thenReturn(Optional.of(local));

    MicrosoftLoginResult result =
        resolver(false).resolve(new MicrosoftIdentity("ana@cefa.es", OID, "Ana"));

    assertThat(result.errorCode()).isEqualTo(MicrosoftUserResolver.ACCOUNT_MISMATCH);
    assertThat(local.getMicrosoftObjectId()).isNotEqualTo(OID);
  }

  @Test
  void rejectsADisabledUser() {
    when(users.findByMicrosoftObjectId(OID))
        .thenReturn(Optional.of(user(AuthProvider.MICROSOFT, false)));

    MicrosoftLoginResult result =
        resolver(false).resolve(new MicrosoftIdentity("ana@cefa.es", OID, "Ana"));

    assertThat(result.errorCode()).isEqualTo(MicrosoftUserResolver.USER_DISABLED);
  }

  @Test
  void rejectsAPasswordOnlyUserAndDoesNotLinkIt() {
    User local = user(AuthProvider.LOCAL, true);
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("ana@cefa.es")).thenReturn(Optional.of(local));

    MicrosoftLoginResult result =
        resolver(false).resolve(new MicrosoftIdentity("ana@cefa.es", OID, "Ana"));

    assertThat(result.errorCode()).isEqualTo(MicrosoftUserResolver.NOT_ALLOWED);
    assertThat(local.getMicrosoftObjectId()).isNull();
  }

  @Test
  void rejectsAnUnknownUserWhenAutoProvisionIsOff() {
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("nuevo@cefa.es")).thenReturn(Optional.empty());

    MicrosoftLoginResult result =
        resolver(false).resolve(new MicrosoftIdentity("nuevo@cefa.es", OID, "Nuevo"));

    assertThat(result.errorCode()).isEqualTo(MicrosoftUserResolver.USER_NOT_FOUND);
    verify(users, never()).save(any());
  }

  @Test
  void createsAUserWithoutPasswordAndWithoutGroupsWhenAutoProvisionIsOn() {
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("nuevo@cefa.es")).thenReturn(Optional.empty());
    when(users.existsByUsernameIgnoreCase("nuevo@cefa.es")).thenReturn(false);

    MicrosoftLoginResult result =
        resolver(true).resolve(new MicrosoftIdentity("nuevo@cefa.es", OID, "Nuevo Usuario"));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(result.isAccepted()).isTrue();
    assertThat(saved.getValue().getAuthProvider()).isEqualTo(AuthProvider.MICROSOFT);
    assertThat(saved.getValue().getPasswordHash()).isNull();
    assertThat(saved.getValue().getEmail()).isEqualTo("nuevo@cefa.es");
    assertThat(saved.getValue().getMicrosoftObjectId()).isEqualTo(OID);
    assertThat(saved.getValue().getDisplayName()).isEqualTo("Nuevo Usuario");
    verify(userGroups, never()).findById(any());
  }

  @Test
  void addsTheNewUserToTheConfiguredDefaultGroup() {
    UserGroup group = new UserGroup();
    group.setId(UUID.randomUUID());
    when(userGroups.findById(group.getId())).thenReturn(Optional.of(group));
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("nuevo@cefa.es")).thenReturn(Optional.empty());

    resolver(true, group.getId().toString())
        .resolve(new MicrosoftIdentity("nuevo@cefa.es", OID, "Nuevo"));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(group.getMemberIds()).containsExactly(saved.getValue().getId());
  }

  @Test
  void neverAddsTheNewUserToAnAllAccessGroupOrToAnInvalidOne() {
    UserGroup allAccess = new UserGroup();
    allAccess.setId(UUID.randomUUID());
    allAccess.setAllAccess(true);
    when(userGroups.findById(allAccess.getId())).thenReturn(Optional.of(allAccess));
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("nuevo@cefa.es")).thenReturn(Optional.empty());

    MicrosoftLoginResult withAllAccess =
        resolver(true, allAccess.getId().toString())
            .resolve(new MicrosoftIdentity("nuevo@cefa.es", OID, "Nuevo"));
    MicrosoftLoginResult withGarbage =
        resolver(true, "no-es-un-uuid")
            .resolve(new MicrosoftIdentity("nuevo@cefa.es", OID, "Nuevo"));
    MicrosoftLoginResult withMissing =
        resolver(true, UUID.randomUUID().toString())
            .resolve(new MicrosoftIdentity("nuevo@cefa.es", OID, "Nuevo"));

    assertThat(allAccess.getMemberIds()).isEmpty();
    assertThat(withAllAccess.isAccepted()).isTrue();
    assertThat(withGarbage.isAccepted()).isTrue();
    assertThat(withMissing.isAccepted()).isTrue();
  }

  @Test
  void rejectsAutoProvisionWhenTheUsernameIsTaken() {
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("nuevo@cefa.es")).thenReturn(Optional.empty());
    when(users.existsByUsernameIgnoreCase("nuevo@cefa.es")).thenReturn(true);

    MicrosoftLoginResult result =
        resolver(true).resolve(new MicrosoftIdentity("nuevo@cefa.es", OID, "Nuevo"));

    assertThat(result.errorCode()).isEqualTo(MicrosoftUserResolver.ACCOUNT_MISMATCH);
    verify(users, never()).save(any());
  }

  @Test
  void rejectsATokenWithoutUsableEmail() {
    when(users.findByMicrosoftObjectId(OID)).thenReturn(Optional.empty());

    MicrosoftLoginResult result = resolver(true).resolve(new MicrosoftIdentity(null, OID, "Ana"));

    assertThat(result.errorCode()).isEqualTo(MicrosoftUserResolver.EMAIL_MISSING);
  }
}
