package es.cefa.tv.core.audit.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deduce la acción y el tipo de entidad auditados a partir del método HTTP y de la plantilla de la
 * ruta: {@code POST /players/{id}/restart} es {@code PLAYER_RESTART}. Un endpoint nuevo queda
 * auditado sin tocar nada.
 */
public final class AuditActionResolver {

  /** Acción y entidad deducidas de una petición. */
  public record Resolution(String action, String entityType) {

    /**
     * Indica si la acción crea una entidad.
     *
     * @return {@code true} si termina en {@code _CREATE}, {@code _UPLOAD} o {@code _CREATE_WEB}
     */
    public boolean isCreate() {
      return action.endsWith("_CREATE") || action.endsWith("_UPLOAD") || action.endsWith("_WEB");
    }

    /**
     * Indica si la acción borra una entidad.
     *
     * @return {@code true} si termina en {@code _DELETE}
     */
    public boolean isDelete() {
      return action.endsWith("_DELETE");
    }
  }

  private static final String API_PREFIX = "/api/v1/";
  private static final Map<String, String> ENTITY_BY_BASE =
      Map.ofEntries(
          Map.entry("players", "PLAYER"),
          Map.entry("media", "MEDIA"),
          Map.entry("playlists", "PLAYLIST"),
          Map.entry("schedules", "SCHEDULE"),
          Map.entry("groups", "PLAYER_GROUP"),
          Map.entry("users", "USER"),
          Map.entry("user-groups", "USER_GROUP"),
          Map.entry("pairings", "PLAYER"),
          Map.entry("auth", "USER"),
          Map.entry("folders", "FOLDER"),
          Map.entry("sites", "SITE"),
          Map.entry("settings", "SETTINGS"));
  private static final Map<String, String> PREFIX_BY_BASE =
      Map.ofEntries(
          Map.entry("players", "PLAYER"),
          Map.entry("media", "MEDIA"),
          Map.entry("playlists", "PLAYLIST"),
          Map.entry("schedules", "SCHEDULE"),
          Map.entry("groups", "PLAYER_GROUP"),
          Map.entry("users", "USER"),
          Map.entry("user-groups", "USER_GROUP"),
          Map.entry("pairings", "PAIRING"),
          Map.entry("folders", "FOLDER"),
          Map.entry("sites", "SITE"),
          Map.entry("auth", "AUTH"));
  private static final Map<String, String> OVERRIDES =
      Map.of(
          "POST users/{id}/password", "USER_PASSWORD_RESET",
          "POST media/web", "MEDIA_CREATE_WEB",
          "POST auth/password", "AUTH_PASSWORD_CHANGE",
          "POST players/aracast", "PLAYER_ARACAST_CREATE");

  /** Evita la instanciación de la clase de utilidades. */
  private AuditActionResolver() {}

  /**
   * Deduce la acción de una petición.
   *
   * @param method método HTTP
   * @param pattern plantilla de la ruta, como {@code /api/v1/players/{id}/restart}
   * @param multipart si la petición es una subida de ficheros
   * @return la acción y el tipo de entidad
   */
  public static Resolution resolve(String method, String pattern, boolean multipart) {
    String relative = relative(pattern);
    String[] segments = relative.split("/");
    String base = segments[0];
    String entity =
        ENTITY_BY_BASE.getOrDefault(base, base.toUpperCase(Locale.ROOT).replace('-', '_'));
    String override = OVERRIDES.get(method + " " + relative);
    if (override != null) {
      return new Resolution(override, entity);
    }
    List<String> tail =
        Arrays.stream(segments).skip(1).filter(segment -> !segment.startsWith("{")).toList();
    if ("media".equals(base) && "POST".equals(method) && tail.isEmpty() && multipart) {
      return new Resolution("MEDIA_UPLOAD", entity);
    }
    if ("settings".equals(base) && !tail.isEmpty()) {
      return new Resolution(upper(tail.get(0)) + "_SETTINGS_" + verb(method), entity);
    }
    String prefix =
        PREFIX_BY_BASE.getOrDefault(base, base.toUpperCase(Locale.ROOT).replace('-', '_'));
    String suffix = tail.isEmpty() ? verb(method) : upper(String.join("_", tail));
    return new Resolution(prefix + "_" + suffix, entity);
  }

  /**
   * Quita el prefijo de la API de una plantilla de ruta.
   *
   * @param pattern plantilla
   * @return la ruta relativa, sin barra inicial
   */
  public static String relative(String pattern) {
    return pattern.startsWith(API_PREFIX) ? pattern.substring(API_PREFIX.length()) : pattern;
  }

  /** Verbo por defecto de un método HTTP. */
  private static String verb(String method) {
    return switch (method) {
      case "POST" -> "CREATE";
      case "PUT", "PATCH" -> "UPDATE";
      case "DELETE" -> "DELETE";
      default -> method;
    };
  }

  /** Pasa a mayúsculas con guion bajo. */
  private static String upper(String text) {
    return text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
  }
}
