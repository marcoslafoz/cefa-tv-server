package es.cefa.tv.core.audit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Prepara los datos de una petición para guardarlos en la auditoría: elimina contraseñas, tokens y
 * demás secretos, y acota el tamaño de textos, listas y anidamiento.
 */
@Component
@RequiredArgsConstructor
public class AuditSanitizer {

  private static final Set<String> SECRET_FRAGMENTS =
      Set.of(
          "password",
          "secret",
          "token",
          "authorization",
          "cookie",
          "apikey",
          "api_key",
          "hash",
          "credential");
  private static final Set<String> SECRET_NAMES = Set.of("code", "key");
  private static final int MAX_STRING = 300;
  private static final int MAX_ITEMS = 50;
  private static final int MAX_DEPTH = 4;
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  /**
   * Convierte el cuerpo de una petición en un mapa sin secretos.
   *
   * @param body cuerpo ya deserializado
   * @return el mapa saneado, o {@code null} si no se puede representar como objeto
   */
  public Map<String, Object> body(Object body) {
    if (body == null) {
      return null;
    }
    try {
      return map(objectMapper.convertValue(body, MAP_TYPE));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Sanea un mapa de detalle.
   *
   * @param raw mapa original
   * @return una copia sin secretos y acotada, o {@code null} si el original es nulo
   */
  public Map<String, Object> map(Map<String, Object> raw) {
    return raw == null ? null : sanitizeMap(raw, 0);
  }

  /**
   * Acota un texto a la longitud máxima de la auditoría.
   *
   * @param value texto
   * @return el texto, recortado si es largo
   */
  public static String clip(String value) {
    if (value == null || value.length() <= MAX_STRING) {
      return value;
    }
    return value.substring(0, MAX_STRING) + "...";
  }

  /** Indica si un nombre de campo corresponde a un secreto. */
  static boolean isSecret(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    return SECRET_NAMES.contains(lower) || SECRET_FRAGMENTS.stream().anyMatch(lower::contains);
  }

  /** Sanea un mapa recursivamente. */
  private Map<String, Object> sanitizeMap(Map<?, ?> raw, int depth) {
    Map<String, Object> result = new LinkedHashMap<>();
    int count = 0;
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      String key = String.valueOf(entry.getKey());
      if (isSecret(key)) {
        continue;
      }
      if (count++ >= MAX_ITEMS) {
        result.put("_truncated", true);
        break;
      }
      result.put(key, sanitizeValue(entry.getValue(), depth + 1));
    }
    return result;
  }

  /** Sanea un valor según su tipo. */
  private Object sanitizeValue(Object value, int depth) {
    if (value == null || value instanceof Number || value instanceof Boolean) {
      return value;
    }
    if (depth > MAX_DEPTH) {
      return "...";
    }
    if (value instanceof Map<?, ?> map) {
      return sanitizeMap(map, depth);
    }
    if (value instanceof Collection<?> collection) {
      List<Object> items = new ArrayList<>();
      for (Object item : collection) {
        if (items.size() >= MAX_ITEMS) {
          items.add("...");
          break;
        }
        items.add(sanitizeValue(item, depth + 1));
      }
      return items;
    }
    return clip(value.toString());
  }
}
