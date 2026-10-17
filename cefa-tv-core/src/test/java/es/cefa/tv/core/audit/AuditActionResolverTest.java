package es.cefa.tv.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.audit.web.AuditActionResolver;
import es.cefa.tv.core.audit.web.AuditActionResolver.Resolution;
import org.junit.jupiter.api.Test;

/** Pruebas de la deducción de acción y entidad a partir del método y la ruta. */
class AuditActionResolverTest {

  private Resolution resolve(String method, String pattern) {
    return AuditActionResolver.resolve(method, pattern, false);
  }

  @Test
  void crudRoutesBecomeEntityVerbs() {
    assertThat(resolve("POST", "/api/v1/playlists").action()).isEqualTo("PLAYLIST_CREATE");
    assertThat(resolve("PUT", "/api/v1/playlists/{id}").action()).isEqualTo("PLAYLIST_UPDATE");
    assertThat(resolve("PATCH", "/api/v1/players/{id}").action()).isEqualTo("PLAYER_UPDATE");
    assertThat(resolve("DELETE", "/api/v1/schedules/{id}").action()).isEqualTo("SCHEDULE_DELETE");
    assertThat(resolve("POST", "/api/v1/groups").entityType()).isEqualTo("PLAYER_GROUP");
    assertThat(resolve("PUT", "/api/v1/user-groups/{id}").action()).isEqualTo("USER_GROUP_UPDATE");
  }

  @Test
  void actionRoutesUseTheirStaticTail() {
    assertThat(resolve("POST", "/api/v1/players/{id}/restart").action())
        .isEqualTo("PLAYER_RESTART");
    assertThat(resolve("POST", "/api/v1/players/{id}/display/resolution").action())
        .isEqualTo("PLAYER_DISPLAY_RESOLUTION");
    assertThat(resolve("POST", "/api/v1/pairings/claim"))
        .isEqualTo(new Resolution("PAIRING_CLAIM", "PLAYER"));
  }

  @Test
  void specialRoutesHaveStableNames() {
    assertThat(resolve("POST", "/api/v1/auth/login").action()).isEqualTo("AUTH_LOGIN");
    assertThat(resolve("POST", "/api/v1/auth/logout").action()).isEqualTo("AUTH_LOGOUT");
    assertThat(resolve("POST", "/api/v1/auth/password").action()).isEqualTo("AUTH_PASSWORD_CHANGE");
    assertThat(resolve("POST", "/api/v1/users/{id}/password").action())
        .isEqualTo("USER_PASSWORD_RESET");
    assertThat(resolve("POST", "/api/v1/media/web").action()).isEqualTo("MEDIA_CREATE_WEB");
    assertThat(AuditActionResolver.resolve("POST", "/api/v1/media", true).action())
        .isEqualTo("MEDIA_UPLOAD");
    assertThat(resolve("PUT", "/api/v1/settings/audit").action())
        .isEqualTo("AUDIT_SETTINGS_UPDATE");
  }

  @Test
  void createAndDeleteAreRecognised() {
    assertThat(resolve("POST", "/api/v1/user-groups").isCreate()).isTrue();
    assertThat(resolve("DELETE", "/api/v1/user-groups/{id}").isDelete()).isTrue();
    assertThat(resolve("PUT", "/api/v1/user-groups/{id}").isCreate()).isFalse();
  }
}
