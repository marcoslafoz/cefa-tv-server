package es.cefa.tv.core.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.telemetry.service.DisplayResolutions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pruebas de la interpretación de las salidas de vídeo comunicadas por un reproductor. */
class DisplayResolutionsTest {

  private static Map<String, Object> display(String output, boolean connected, boolean selected) {
    return Map.of(
        "output",
        output,
        "connected",
        connected,
        "selected",
        selected,
        "width",
        1920,
        "height",
        1080);
  }

  @Test
  void usesTheSelectedOutput() {
    List<Map<String, Object>> displays =
        List.of(
            display("HDMI-1", true, false),
            Map.of(
                "output",
                "DP-1",
                "connected",
                true,
                "selected",
                true,
                "width",
                2560,
                "height",
                1440));
    assertThat(DisplayResolutions.selectedResolution(displays)).contains("2560x1440");
  }

  @Test
  void returnsNothingWithoutASelectedOutputOrSize() {
    assertThat(DisplayResolutions.selectedResolution(null)).isEmpty();
    assertThat(DisplayResolutions.selectedResolution(List.of(display("HDMI-1", true, false))))
        .isEmpty();
    assertThat(
            DisplayResolutions.selectedResolution(
                List.of(Map.of("output", "HDMI-1", "selected", true))))
        .isEmpty();
  }

  @Test
  void detectsConnectedOutputs() {
    List<Map<String, Object>> displays =
        List.of(display("HDMI-1", true, true), display("DP-1", false, false));
    assertThat(DisplayResolutions.isConnected(displays, "HDMI-1")).isTrue();
    assertThat(DisplayResolutions.isConnected(displays, "DP-1")).isFalse();
    assertThat(DisplayResolutions.isConnected(displays, "VGA-1")).isFalse();
    assertThat(DisplayResolutions.isConnected(null, "HDMI-1")).isFalse();
  }
}
