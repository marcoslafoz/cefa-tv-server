package es.cefa.tv.core.aracast.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Nombres de fichero y de plantilla compatibles con el player. Los contenidos se guardan como
 * {@code <nombre>_<sha1>.<ext>}, igual que hace la aplicación de escritorio, y el player usa ese
 * hash para saber si ya los tiene.
 */
public final class MediaNaming {

  private MediaNaming() {}

  /** SHA-1 en hexadecimal del contenido del fichero. */
  public static String sha1(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] buffer = new byte[1024 * 1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("No se pudo calcular el hash de " + file, e);
    }
  }

  /** Extensión en minúsculas sin el punto, o cadena vacía. */
  public static String extension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  /** Nombre sin extensión. */
  public static String baseName(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot <= 0 ? fileName : fileName.substring(0, dot);
  }

  /**
   * Limpia un nombre para usarlo en el player: sin separadores de ruta ni caracteres de control,
   * espacios normalizados y como mucho 80 caracteres.
   */
  public static String clean(String name) {
    String cleaned =
        name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
    return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
  }

  /**
   * Nombre seguro para el player. El player usa los nombres como patrones, así que cualquier
   * carácter que no sea letra, número, espacio, guion o guion bajo (corchetes, paréntesis...) se
   * sustituye por un espacio.
   *
   * @param name nombre original
   * @return el nombre saneado, de como mucho 80 caracteres
   */
  public static String safe(String name) {
    String cleaned = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N} _-]", " ");
    cleaned = cleaned.replaceAll("\\s+", " ").trim();
    return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
  }

  /**
   * Nombre del contenido en el player a partir del nombre original y el SHA-1 ya conocido.
   *
   * @param originalName nombre original, con extensión
   * @param sha1 SHA-1 del fichero en hexadecimal
   * @return {@code <nombre>_<sha1>.<ext>}
   */
  public static String playerName(String originalName, String sha1) {
    String ext = extension(originalName).replaceAll("[^a-z0-9]", "");
    String base = safe(baseName(originalName));
    if (base.isEmpty()) {
      base = "contenido";
    }
    return base + "_" + sha1 + (ext.isEmpty() ? "" : "." + ext);
  }

  /** Nombre del contenido en el player a partir del nombre original y el fichero. */
  public static String playerName(String originalName, Path file) {
    String ext = extension(originalName);
    String base = clean(baseName(originalName));
    if (base.isEmpty()) {
      base = "contenido";
    }
    return base + "_" + sha1(file) + (ext.isEmpty() ? "" : "." + ext);
  }
}
