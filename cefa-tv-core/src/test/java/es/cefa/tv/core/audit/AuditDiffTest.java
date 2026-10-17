package es.cefa.tv.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.audit.service.AuditDiff;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pruebas del resumen de cambios entre dos estados. */
class AuditDiffTest {

  private Map<String, Object> state(String name, Object items) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("name", name);
    state.put("items", items);
    return state;
  }

  @Test
  void reportsOnlyTheFieldsThatChanged() {
    List<Map<String, Object>> changes =
        AuditDiff.changes(state("Entrada", List.of("a")), state("Recepción", List.of("a")));

    assertThat(changes).hasSize(1);
    assertThat(changes.get(0))
        .containsEntry("field", "name")
        .containsEntry("before", "Entrada")
        .containsEntry("after", "Recepción");
  }

  @Test
  void noChangesYieldsAnEmptyList() {
    assertThat(AuditDiff.changes(state("A", List.of("x")), state("A", List.of("x")))).isEmpty();
  }

  @Test
  void aMissingSideYieldsNoChanges() {
    assertThat(AuditDiff.changes(null, state("A", List.of()))).isEmpty();
    assertThat(AuditDiff.changes(state("A", List.of()), null)).isEmpty();
  }

  @Test
  void listsAreComparedByContentAndLongOnesAreSummarised() {
    List<String> many = java.util.stream.IntStream.range(0, 20).mapToObj(i -> "m" + i).toList();

    List<Map<String, Object>> changes =
        AuditDiff.changes(state("A", List.of("x")), state("A", many));

    assertThat(changes).hasSize(1);
    assertThat(changes.get(0))
        .containsEntry("before", List.of("x"))
        .containsEntry("after", "20 elementos");
  }

  @Test
  void newAndRemovedFieldsCount() {
    Map<String, Object> before = new LinkedHashMap<>(Map.of("a", "1"));
    Map<String, Object> after = new LinkedHashMap<>(Map.of("b", "2"));

    assertThat(AuditDiff.changes(before, after))
        .extracting(change -> change.get("field"))
        .containsExactly("a", "b");
  }
}
