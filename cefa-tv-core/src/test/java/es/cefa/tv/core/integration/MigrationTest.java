package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Las migraciones conservan usuarios, permisos y accesos: los roles pasan a grupos de usuarios y
 * las concesiones directas a grupos personales.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final String ADMINISTRATORS = "00000000-0000-0000-0000-000000000011";
  private static final String SCHEDULER_ROLE = "00000000-0000-0000-0000-000000000002";
  private static final String VIEWER_ROLE = "00000000-0000-0000-0000-000000000003";

  private void migrate(String target) {
    var config =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    if (target != null) {
      config.target(target);
    }
    config.load().migrate();
  }

  private List<String> column(Statement statement, String sql) throws Exception {
    List<String> values = new ArrayList<>();
    try (ResultSet result = statement.executeQuery(sql)) {
      while (result.next()) {
        values.add(result.getString(1));
      }
    }
    return values;
  }

  private long count(Statement statement, String sql) throws Exception {
    return Long.parseLong(column(statement, sql).get(0));
  }

  private boolean tableExists(Statement statement, String table) throws Exception {
    return count(
            statement,
            "select count(*) from information_schema.tables where table_name = '" + table + "'")
        == 1;
  }

  private boolean columnExists(Statement statement, String table, String column) throws Exception {
    return count(
            statement,
            "select count(*) from information_schema.columns where table_name = '"
                + table
                + "' and column_name = '"
                + column
                + "'")
        == 1;
  }

  private String groupId(Statement statement, String name) throws Exception {
    return column(statement, "select id from user_groups where name = '" + name + "'").get(0);
  }

  private List<String> memberNames(Statement statement, String groupName) throws Exception {
    return column(
        statement,
        "select u.username from user_group_members m join users u on u.id = m.user_id"
            + " join user_groups g on g.id = m.user_group_id where g.name = '"
            + groupName
            + "' order by u.username");
  }

  @Test
  void rolesBecomeGroupsAndDirectGrantsBecomePersonalGroups() throws Exception {
    migrate("2");
    String adminId = UUID.randomUUID().toString();
    String editorId = UUID.randomUUID().toString();
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "insert into users (id, username, password_hash, role, created_at) values ('"
              + adminId
              + "', 'jefe', 'hash', 'ADMIN', now()), ('"
              + editorId
              + "', 'editor', 'hash', 'EDITOR', now())");
      statement.executeUpdate(
          "insert into enrollment_keys (id, code, name, created_at) values ('"
              + UUID.randomUUID()
              + "', 'ABCD-EFGH-JKMN', 'sede', now())");
      migrate("8");

      String customRole = UUID.randomUUID().toString();
      String analystId = UUID.randomUUID().toString();
      String viewerId = UUID.randomUUID().toString();
      String directId = UUID.randomUUID().toString();
      String playerGroup = UUID.randomUUID().toString();
      String site = UUID.randomUUID().toString();
      String previous = UUID.randomUUID().toString();
      statement.executeUpdate(
          "insert into roles (id, name, builtin, permissions) values ('"
              + customRole
              + "', 'Analistas', false, ARRAY['AUDIT_VIEW','PLAYER_VIEW'])");
      statement.executeUpdate(
          "insert into users (id, username, display_name, password_hash, role_id, created_at)"
              + " values ('"
              + analystId
              + "', 'analista', 'Analista', 'hash', '"
              + customRole
              + "', now()), ('"
              + viewerId
              + "', 'lector', 'Lector', 'hash', '"
              + VIEWER_ROLE
              + "', now()), ('"
              + directId
              + "', 'directo', 'Directo', 'hash', '"
              + SCHEDULER_ROLE
              + "', now())");
      statement.executeUpdate(
          "insert into player_groups (id, name) values ('" + playerGroup + "', 'Tienda')");
      statement.executeUpdate(
          "insert into sites (id, name, created_at) values ('" + site + "', 'Zaragoza', now())");
      statement.executeUpdate(
          "insert into access_grants (id, user_id, player_group_id, level) values ('"
              + UUID.randomUUID()
              + "', '"
              + directId
              + "', '"
              + playerGroup
              + "', 'OPERATE')");
      statement.executeUpdate(
          "insert into access_grants (id, user_id, site_id, level) values ('"
              + UUID.randomUUID()
              + "', '"
              + directId
              + "', '"
              + site
              + "', 'VIEW')");
      statement.executeUpdate(
          "insert into user_groups (id, name) values ('" + previous + "', 'Programadores')");
      statement.executeUpdate(
          "insert into user_group_members (user_group_id, user_id) values ('"
              + previous
              + "', '"
              + editorId
              + "')");

      migrate(null);

      assertThat(tableExists(statement, "roles")).isFalse();
      assertThat(tableExists(statement, "enrollment_keys")).isFalse();
      assertThat(columnExists(statement, "users", "role_id")).isFalse();
      assertThat(columnExists(statement, "access_grants", "user_id")).isFalse();
      assertThat(count(statement, "select count(*) from users")).isEqualTo(5);
      assertThat(
              column(statement, "select display_name from users where username = 'editor'").get(0))
          .isEqualTo("editor");

      assertThat(
              count(
                  statement,
                  "select count(*) from user_groups where id = '"
                      + ADMINISTRATORS
                      + "' and builtin and all_access and name = 'Administradores'"
                      + " and cardinality(permissions) = 9"))
          .isEqualTo(1);
      assertThat(memberNames(statement, "Administradores")).containsExactly("jefe");

      assertThat(memberNames(statement, "Programadores")).containsExactly("directo", "editor");
      assertThat(memberNames(statement, "Programadores (previo)")).containsExactly("editor");
      assertThat(
              column(
                      statement,
                      "select array_to_string(permissions, ',') from user_groups"
                          + " where name = 'Programadores'")
                  .get(0))
          .contains("SCHEDULE_MANAGE")
          .contains("MEDIA_MANAGE")
          .doesNotContain("USER_MANAGE");
      assertThat(memberNames(statement, "Visualizadores")).containsExactly("lector");
      assertThat(
              column(
                      statement,
                      "select array_to_string(permissions, ',') from user_groups"
                          + " where name = 'Visualizadores'")
                  .get(0))
          .isEqualTo("PLAYER_VIEW");

      assertThat(memberNames(statement, "Analistas")).containsExactly("analista");
      assertThat(
              column(
                      statement,
                      "select array_to_string(permissions, ',') from user_groups"
                          + " where name = 'Analistas'")
                  .get(0))
          .isEqualTo("AUDIT_VIEW,PLAYER_VIEW");

      assertThat(memberNames(statement, "directo")).containsExactly("directo");
      String personal = groupId(statement, "directo");
      assertThat(
              column(
                      statement,
                      "select cardinality(permissions) from user_groups where id = '"
                          + personal
                          + "'")
                  .get(0))
          .isEqualTo("0");
      assertThat(
              count(
                  statement,
                  "select count(*) from access_grants where user_group_id = '"
                      + personal
                      + "' and player_group_id = '"
                      + playerGroup
                      + "' and level = 'OPERATE'"))
          .isEqualTo(1);
      assertThat(
              count(
                  statement,
                  "select count(*) from access_grants where user_group_id = '"
                      + personal
                      + "' and site_id = '"
                      + site
                      + "' and level = 'VIEW'"))
          .isEqualTo(1);
      assertThat(
              count(
                  statement,
                  "select count(*) from user_groups where name in ('editor', 'lector', 'jefe',"
                      + " 'analista')"))
          .isZero();
    }
  }
}
