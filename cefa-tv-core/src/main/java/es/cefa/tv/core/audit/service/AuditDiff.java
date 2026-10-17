package es.cefa.tv.core.audit.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Calcula el resumen de cambios entre dos estados de una entidad. */
public final class AuditDiff {

  private static final int MAX_LIST_ITEMS = 10;
  private static final int MAX_TEXT = 200;

  /** Evita la instanciación de la clase de utilidades. */
  private AuditDiff() {}

  /**
   * Lista los campos que difieren entre el estado anterior y el posterior.
   *
   * @param before estado anterior
   * @param after estado posterior
   * @return una lista de {@code {field, before, after}} ordenada por campo; vacía si no hay cambios
   */
  public static List<Map<String, Object>> changes(
      Map<String, Object> before, Map<String, Object> after) {
    List<Map<String, Object>> changes = new ArrayList<>();
    if (before == null || after == null) {
      return changes;
    }
    TreeSet<String> fields = new TreeSet<>(before.keySet());
    fields.addAll(after.keySet());
    for (String field : fields) {
      Object previous = before.get(field);
      Object current = after.get(field);
      if (!Objects.equals(normalize(previous), normalize(current))) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("field", field);
        change.put("before", summarize(previous));
        change.put("after", summarize(current));
        changes.add(change);
      }
    }
    return changes;
  }

  /** Convierte un valor en una forma comparable. */
  private static Object normalize(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().map(AuditDiff::normalize).toList();
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> normalized = new LinkedHashMap<>();
      map.forEach((key, item) -> normalized.put(String.valueOf(key), normalize(item)));
      return normalized;
    }
    return value.toString();
  }

  /** Reduce un valor a una forma corta para mostrarlo. */
  private static Object summarize(Object value) {
    if (value == null || value instanceof Number || value instanceof Boolean) {
      return value;
    }
    if (value instanceof Collection<?> collection) {
      if (collection.size() > MAX_LIST_ITEMS) {
        return collection.size() + " elementos";
      }
      return collection.stream().map(AuditDiff::summarize).toList();
    }
    String text = value.toString();
    return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "..." : text;
  }
}
