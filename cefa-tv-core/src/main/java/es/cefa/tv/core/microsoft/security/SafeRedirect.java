package es.cefa.tv.core.microsoft.security;

import java.util.Optional;
import java.util.Set;

/**
 * Valida los destinos de redirección posteriores al inicio de sesión para evitar redirecciones
 * abiertas.
 */
public final class SafeRedirect {

  private static final int MAX_LENGTH = 512;
  private static final Set<String> LOCALES = Set.of("es", "en");

  private SafeRedirect() {}

  /**
   * Acepta únicamente rutas relativas del mismo sitio: empiezan por una sola barra y no contienen
   * esquema, barras invertidas ni caracteres de control.
   *
   * @param path ruta pedida por el cliente
   * @return la ruta si es segura
   */
  public static Optional<String> sanitizePath(String path) {
    if (path == null || path.isBlank() || path.length() > MAX_LENGTH) {
      return Optional.empty();
    }
    if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")) {
      return Optional.empty();
    }
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c < 0x20 || c == 0x7f) {
        return Optional.empty();
      }
    }
    if (path.contains("://") || path.toLowerCase().contains("%2f%2f") || path.contains("/..")) {
      return Optional.empty();
    }
    return Optional.of(path);
  }

  /**
   * Calcula la ruta de destino: la pedida si es segura y, si no, el panel en el idioma indicado.
   *
   * @param redirect ruta pedida por el cliente
   * @param locale idioma pedido, {@code es} o {@code en}
   * @return la ruta de destino relativa a la interfaz
   */
  public static String targetPath(String redirect, String locale) {
    return sanitizePath(redirect)
        .orElseGet(
            () ->
                locale != null && LOCALES.contains(locale)
                    ? "/" + locale + "/dashboard"
                    : "/dashboard");
  }

  /**
   * Compone la URL absoluta de destino sobre la URL base de la interfaz.
   *
   * @param appBaseUrl URL base de la interfaz
   * @param path ruta relativa ya validada
   * @return la URL completa
   */
  public static String absolute(String appBaseUrl, String path) {
    String base =
        appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    return base + path;
  }
}
