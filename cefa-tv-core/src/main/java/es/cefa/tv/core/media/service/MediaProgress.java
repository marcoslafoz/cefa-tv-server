package es.cefa.tv.core.media.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Avance, de 0 a 100, de los medios que se están comprimiendo. Vive en memoria: solo interesa
 * mientras el medio está en proceso.
 */
public final class MediaProgress {

  private static final Map<UUID, Integer> VALUES = new ConcurrentHashMap<>();

  private MediaProgress() {}

  /**
   * Anota el avance de un medio.
   *
   * @param id medio
   * @param percent porcentaje de 0 a 100
   */
  public static void set(UUID id, int percent) {
    VALUES.put(id, percent);
  }

  /**
   * Avance de un medio.
   *
   * @param id medio
   * @return el porcentaje, o {@code null} si no se está procesando
   */
  public static Integer get(UUID id) {
    return VALUES.get(id);
  }

  /**
   * Olvida el avance de un medio.
   *
   * @param id medio
   */
  public static void clear(UUID id) {
    VALUES.remove(id);
  }
}
