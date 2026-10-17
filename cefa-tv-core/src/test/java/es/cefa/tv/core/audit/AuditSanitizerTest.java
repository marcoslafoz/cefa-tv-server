package es.cefa.tv.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.audit.service.AuditSanitizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pruebas de que la auditoría nunca guarda secretos y acota el tamaño de los datos. */
class AuditSanitizerTest {

  private record Body(
      String username,
      String password,
      String newPassword,
      String code,
      String token,
      String name) {}

  private final AuditSanitizer sanitizer = new AuditSanitizer(new ObjectMapper());

  @Test
  void secretFieldsAreDropped() {
    Map<String, Object> result =
        sanitizer.body(new Body("ana", "p4ss", "n3w", "ABC-123", "tkn", "Recepción"));

    assertThat(result).containsEntry("username", "ana").containsEntry("name", "Recepción");
    assertThat(result).doesNotContainKeys("password", "newPassword", "code", "token");
  }

  @Test
  void nestedSecretsAreDroppedToo() {
    Map<String, Object> result =
        sanitizer.map(
            Map.of(
                "user",
                Map.of("name", "ana", "passwordHash", "abc"),
                "credentials",
                List.of(Map.of("apiKey", "k"))));

    assertThat(result.toString()).doesNotContain("abc").doesNotContain("apiKey");
    assertThat(result).doesNotContainKey("credentials");
  }

  @Test
  void longTextsAndLongListsAreClipped() {
    Map<String, Object> result =
        sanitizer.map(
            Map.of(
                "text",
                "x".repeat(1000),
                "list",
                java.util.stream.IntStream.range(0, 200).boxed().toList()));

    assertThat((String) result.get("text")).hasSize(303).endsWith("...");
    assertThat((List<?>) result.get("list")).hasSize(51);
  }

  @Test
  void nullBodiesAreHandled() {
    assertThat(sanitizer.body(null)).isNull();
    assertThat(sanitizer.map(null)).isNull();
  }
}
