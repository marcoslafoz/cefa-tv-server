package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** La migración que deja las carpetas solo para medios conserva los datos de medios y playlists. */
@Testcontainers(disabledWithoutDocker = true)
class FolderMigrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  private boolean columnExists(Statement statement, String table, String column) throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "select count(*) from information_schema.columns where table_name = '"
                + table
                + "' and column_name = '"
                + column
                + "'")) {
      result.next();
      return result.getInt(1) == 1;
    }
  }

  private long count(Statement statement, String sql) throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  @Test
  void playlistFoldersDisappearAndMediaFoldersAndContentSurvive() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("7")
        .load()
        .migrate();
    String mediaFolder = UUID.randomUUID().toString();
    String mediaChild = UUID.randomUUID().toString();
    String playlistFolder = UUID.randomUUID().toString();
    String playlistChild = UUID.randomUUID().toString();
    String media = UUID.randomUUID().toString();
    String playlist = UUID.randomUUID().toString();
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "insert into folders (id, kind, name, parent_id, created_at) values ('"
              + mediaFolder
              + "', 'MEDIA', 'Cartelería', null, now()), ('"
              + mediaChild
              + "', 'MEDIA', 'Octubre', '"
              + mediaFolder
              + "', now()), ('"
              + playlistFolder
              + "', 'PLAYLIST', 'Cartelería', null, now()), ('"
              + playlistChild
              + "', 'PLAYLIST', 'Campañas', '"
              + playlistFolder
              + "', now())");
      statement.executeUpdate(
          "insert into media_assets (id, name, type, status, created_at, folder_id) values ('"
              + media
              + "', 'logo.png', 'IMAGE', 'READY', now(), '"
              + mediaChild
              + "')");
      statement.executeUpdate(
          "insert into playlists (id, name, hidden, created_at, updated_at, folder_id) values ('"
              + playlist
              + "', 'Entrada', false, now(), now(), '"
              + playlistChild
              + "')");

      Flyway.configure()
          .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
          .load()
          .migrate();

      assertThat(columnExists(statement, "playlists", "folder_id")).isFalse();
      assertThat(columnExists(statement, "folders", "kind")).isFalse();
      assertThat(count(statement, "select count(*) from folders")).isEqualTo(2);
      assertThat(count(statement, "select count(*) from folders where id = '" + mediaFolder + "'"))
          .isEqualTo(1);
      assertThat(count(statement, "select count(*) from folders where id = '" + mediaChild + "'"))
          .isEqualTo(1);
      assertThat(
              count(
                  statement,
                  "select count(*) from media_assets where id = '"
                      + media
                      + "' and folder_id = '"
                      + mediaChild
                      + "'"))
          .isEqualTo(1);
      assertThat(count(statement, "select count(*) from playlists where id = '" + playlist + "'"))
          .isEqualTo(1);
      assertThatThrownBy(
              () ->
                  statement.executeUpdate(
                      "insert into folders (id, name, parent_id, created_at) values ('"
                          + UUID.randomUUID()
                          + "', 'CARTELERÍA', null, now())"))
          .isInstanceOf(SQLException.class);
    }
  }
}
