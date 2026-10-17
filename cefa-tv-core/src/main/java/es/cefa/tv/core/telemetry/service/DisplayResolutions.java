package es.cefa.tv.core.telemetry.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Utilidades para interpretar la lista de salidas de vídeo que comunica un reproductor. */
public final class DisplayResolutions {

  private DisplayResolutions() {}

  /**
   * Obtiene la resolución de la salida elegida por el reproductor.
   *
   * @param displays salidas comunicadas
   * @return la resolución con formato {@code 1920x1080} si hay una salida elegida con tamaño
   */
  public static Optional<String> selectedResolution(List<Map<String, Object>> displays) {
    if (displays == null) {
      return Optional.empty();
    }
    return displays.stream()
        .filter(display -> Boolean.TRUE.equals(display.get("selected")))
        .filter(
            display ->
                display.get("width") instanceof Number && display.get("height") instanceof Number)
        .map(
            display ->
                ((Number) display.get("width")).intValue()
                    + "x"
                    + ((Number) display.get("height")).intValue())
        .findFirst();
  }

  /**
   * Indica si una salida existe y está conectada entre las comunicadas.
   *
   * @param displays salidas comunicadas
   * @param output nombre de la salida
   * @return {@code true} si la salida figura como conectada
   */
  public static boolean isConnected(List<Map<String, Object>> displays, String output) {
    return displays != null
        && displays.stream()
            .anyMatch(
                display ->
                    output.equals(display.get("output"))
                        && Boolean.TRUE.equals(display.get("connected")));
  }
}
