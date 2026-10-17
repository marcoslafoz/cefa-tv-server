package es.cefa.tv.core.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessPolicy;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Matriz de decisión de la política de acceso: rol por concesión por grupo de reproductores. */
class AccessPolicyTest {

  private static final UUID GROUP_A = UUID.randomUUID();
  private static final UUID GROUP_B = UUID.randomUUID();
  private static final UUID SITE_A = UUID.randomUUID();
  private static final UUID SITE_B = UUID.randomUUID();

  private static AuthenticatedUser user(
      boolean admin, Set<Permission> permissions, Map<UUID, AccessLevel> grants) {
    return user(admin, permissions, grants, Map.of());
  }

  private static AuthenticatedUser user(
      boolean admin,
      Set<Permission> permissions,
      Map<UUID, AccessLevel> grants,
      Map<UUID, AccessLevel> siteGrants) {
    return new AuthenticatedUser(
        UUID.randomUUID(),
        "user",
        "User",
        java.util.List.of(),
        admin,
        permissions,
        grants,
        siteGrants,
        null,
        "LOCAL");
  }

  @ParameterizedTest
  @EnumSource(Permission.class)
  void adminHasEveryPermissionAndAccessToEveryGroupAndToUngroupedPlayers(Permission permission) {
    AuthenticatedUser admin = user(true, EnumSet.noneOf(Permission.class), Map.of());

    assertThat(AccessPolicy.hasPermission(admin, permission)).isTrue();
    assertThat(AccessPolicy.canAccessGroup(admin, GROUP_A, AccessLevel.OPERATE)).isTrue();
    assertThat(AccessPolicy.canAccessGroup(admin, null, AccessLevel.OPERATE)).isTrue();
    assertThat(AccessPolicy.canActOnPlayer(admin, permission, null, AccessLevel.OPERATE)).isTrue();
  }

  @Test
  void roleWithoutPermissionIsDeniedEvenWithOperateGrant() {
    AuthenticatedUser viewer =
        user(false, EnumSet.of(Permission.PLAYER_VIEW), Map.of(GROUP_A, AccessLevel.OPERATE));

    assertThat(
            AccessPolicy.canActOnPlayer(
                viewer, Permission.SCHEDULE_MANAGE, GROUP_A, AccessLevel.OPERATE))
        .isFalse();
    assertThat(
            AccessPolicy.canActOnPlayer(viewer, Permission.PLAYER_VIEW, GROUP_A, AccessLevel.VIEW))
        .isTrue();
  }

  @Test
  void permissionWithoutGrantIsDeniedOnTheGroup() {
    AuthenticatedUser scheduler =
        user(false, EnumSet.of(Permission.SCHEDULE_MANAGE), Map.of(GROUP_A, AccessLevel.OPERATE));

    assertThat(
            AccessPolicy.canActOnPlayer(
                scheduler, Permission.SCHEDULE_MANAGE, GROUP_B, AccessLevel.VIEW))
        .isFalse();
    assertThat(
            AccessPolicy.canActOnPlayer(
                scheduler, Permission.SCHEDULE_MANAGE, GROUP_A, AccessLevel.OPERATE))
        .isTrue();
  }

  @Test
  void viewGrantDoesNotCoverOperate() {
    AuthenticatedUser scheduler =
        user(false, EnumSet.of(Permission.SCHEDULE_MANAGE), Map.of(GROUP_A, AccessLevel.VIEW));

    assertThat(AccessPolicy.canAccessGroup(scheduler, GROUP_A, AccessLevel.VIEW)).isTrue();
    assertThat(AccessPolicy.canAccessGroup(scheduler, GROUP_A, AccessLevel.OPERATE)).isFalse();
  }

  @Test
  void operateGrantCoversView() {
    AuthenticatedUser scheduler =
        user(false, EnumSet.of(Permission.PLAYER_VIEW), Map.of(GROUP_A, AccessLevel.OPERATE));

    assertThat(AccessPolicy.canAccessGroup(scheduler, GROUP_A, AccessLevel.VIEW)).isTrue();
  }

  @Test
  void ungroupedPlayersAreOnlyForAdmins() {
    AuthenticatedUser operator =
        user(
            false,
            EnumSet.allOf(Permission.class),
            Map.of(GROUP_A, AccessLevel.OPERATE, GROUP_B, AccessLevel.OPERATE));

    assertThat(AccessPolicy.canAccessGroup(operator, null, AccessLevel.VIEW)).isFalse();
    assertThat(
            AccessPolicy.canActOnPlayer(operator, Permission.PLAYER_VIEW, null, AccessLevel.VIEW))
        .isFalse();
  }

  @Test
  void hasAnyPermissionAcceptsAnyOfThem() {
    AuthenticatedUser user = user(false, EnumSet.of(Permission.MEDIA_MANAGE), Map.of());

    assertThat(
            AccessPolicy.hasAnyPermission(user, Permission.PLAYER_MANAGE, Permission.MEDIA_MANAGE))
        .isTrue();
    assertThat(
            AccessPolicy.hasAnyPermission(user, Permission.PLAYER_MANAGE, Permission.USER_MANAGE))
        .isFalse();
  }

  @Test
  void accessLevelCoverageIsOrdered() {
    assertThat(AccessLevel.OPERATE.covers(AccessLevel.VIEW)).isTrue();
    assertThat(AccessLevel.VIEW.covers(AccessLevel.OPERATE)).isFalse();
    assertThat(AccessLevel.max(AccessLevel.VIEW, AccessLevel.OPERATE))
        .isEqualTo(AccessLevel.OPERATE);
  }

  @Test
  void aSiteGrantReachesTheSitePlayersEvenWithoutAGroup() {
    AuthenticatedUser user =
        user(
            false,
            EnumSet.of(Permission.PLAYER_VIEW, Permission.SCHEDULE_MANAGE),
            Map.of(),
            Map.of(SITE_A, AccessLevel.OPERATE));

    assertThat(AccessPolicy.canAccessSite(user, SITE_A, AccessLevel.OPERATE)).isTrue();
    assertThat(AccessPolicy.canAccessPlayer(user, null, SITE_A, AccessLevel.OPERATE)).isTrue();
    assertThat(AccessPolicy.canAccessPlayer(user, GROUP_A, SITE_A, AccessLevel.VIEW)).isTrue();
    assertThat(AccessPolicy.canAccessPlayer(user, GROUP_A, SITE_B, AccessLevel.VIEW)).isFalse();
    assertThat(AccessPolicy.canAccessPlayer(user, null, null, AccessLevel.VIEW)).isFalse();
    assertThat(
            AccessPolicy.canActOnPlayer(
                user, Permission.PLAYER_VIEW, null, SITE_A, AccessLevel.VIEW))
        .isTrue();
    assertThat(
            AccessPolicy.canActOnPlayer(
                user, Permission.PLAYER_MANAGE, null, SITE_A, AccessLevel.VIEW))
        .isFalse();
  }

  @Test
  void aViewSiteGrantDoesNotAllowOperating() {
    AuthenticatedUser user =
        user(false, EnumSet.of(Permission.PLAYER_VIEW), Map.of(), Map.of(SITE_A, AccessLevel.VIEW));

    assertThat(AccessPolicy.canAccessPlayer(user, null, SITE_A, AccessLevel.VIEW)).isTrue();
    assertThat(AccessPolicy.canAccessPlayer(user, null, SITE_A, AccessLevel.OPERATE)).isFalse();
  }

  @Test
  void groupAndSiteGrantsAddUpForThePlayerThatIsInBoth() {
    AuthenticatedUser user =
        user(
            false,
            EnumSet.of(Permission.PLAYER_VIEW),
            Map.of(GROUP_A, AccessLevel.VIEW),
            Map.of(SITE_B, AccessLevel.OPERATE));

    assertThat(AccessPolicy.canAccessPlayer(user, GROUP_A, SITE_B, AccessLevel.OPERATE)).isTrue();
    assertThat(AccessPolicy.canAccessPlayer(user, GROUP_A, SITE_A, AccessLevel.OPERATE)).isFalse();
    assertThat(AccessPolicy.canAccessPlayer(user, GROUP_A, SITE_A, AccessLevel.VIEW)).isTrue();
  }

  @Test
  void adminReachesEverySite() {
    AuthenticatedUser admin = user(true, EnumSet.noneOf(Permission.class), Map.of());

    assertThat(AccessPolicy.canAccessSite(admin, SITE_A, AccessLevel.OPERATE)).isTrue();
    assertThat(AccessPolicy.canAccessPlayer(admin, null, null, AccessLevel.OPERATE)).isTrue();
  }
}
