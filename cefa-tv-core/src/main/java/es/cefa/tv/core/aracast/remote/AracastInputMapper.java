package es.cefa.tv.core.aracast.remote;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduce las entradas de ratón y teclado que envía la interfaz de CEFA TV (coordenadas
 * normalizadas, teclas de navegador) a las acciones que sabe inyectar un Aracast por XTest: clic,
 * rueda, tecla y texto. Cada sesión usa su propia instancia porque recuerda el botón pulsado.
 */
public class AracastInputMapper {

  private static final int ALT = 1;
  private static final int CTRL = 2;
  private static final int META = 4;
  private static final int SHIFT = 8;
  private static final int MAX_TEXT = 500;
  private static final int WHEEL_STEP = 100;
  private static final int MAX_STEPS = 20;
  private static final Set<String> MODIFIER_KEYS =
      Set.of("Shift", "Control", "Alt", "Meta", "CapsLock");
  private static final Map<String, String> KEYS =
      Map.ofEntries(
          Map.entry("Enter", "Return"),
          Map.entry("Backspace", "BackSpace"),
          Map.entry("Tab", "Tab"),
          Map.entry("Escape", "Escape"),
          Map.entry("Delete", "Delete"),
          Map.entry("Insert", "Insert"),
          Map.entry("Home", "Home"),
          Map.entry("End", "End"),
          Map.entry("PageUp", "Prior"),
          Map.entry("PageDown", "Next"),
          Map.entry("ArrowLeft", "Left"),
          Map.entry("ArrowRight", "Right"),
          Map.entry("ArrowUp", "Up"),
          Map.entry("ArrowDown", "Down"),
          Map.entry("F1", "F1"),
          Map.entry("F2", "F2"),
          Map.entry("F3", "F3"),
          Map.entry("F4", "F4"),
          Map.entry("F5", "F5"),
          Map.entry("F6", "F6"),
          Map.entry("F7", "F7"),
          Map.entry("F8", "F8"),
          Map.entry("F9", "F9"),
          Map.entry("F10", "F10"),
          Map.entry("F11", "F11"),
          Map.entry("F12", "F12"));

  private Integer pressedButton;

  /**
   * Convierte un mensaje {@code INPUT} en acciones.
   *
   * @param input mensaje de entrada de la interfaz
   * @return acciones a ejecutar en orden; vacío si no hay nada que hacer
   */
  public List<RemoteInput> map(JsonNode input) {
    String kind = input.path("kind").asText("");
    return switch (kind) {
      case "MOUSE_DOWN" -> {
        pressedButton = button(input.path("button").asText("left"));
        yield List.of();
      }
      case "MOUSE_UP" -> {
        Integer button = pressedButton;
        pressedButton = null;
        yield button == null
            ? List.of()
            : List.of(
                new RemoteInput(
                    "click",
                    coordinate(input, "x"),
                    coordinate(input, "y"),
                    button,
                    1,
                    null,
                    null));
      }
      case "MOUSE_WHEEL" -> wheel(input);
      case "KEY_DOWN" -> key(input);
      case "TEXT" -> {
        String text = input.path("text").asText("");
        yield text.isEmpty()
            ? List.of()
            : List.of(
                new RemoteInput(
                    "text",
                    null,
                    null,
                    null,
                    null,
                    null,
                    text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text));
      }
      default -> List.of();
    };
  }

  private List<RemoteInput> wheel(JsonNode input) {
    double delta = input.path("deltaY").asDouble(0);
    if (delta == 0) {
      return List.of();
    }
    int steps = (int) Math.max(1, Math.min(MAX_STEPS, Math.round(Math.abs(delta) / WHEEL_STEP)));
    return List.of(
        new RemoteInput(
            "scroll",
            coordinate(input, "x"),
            coordinate(input, "y"),
            null,
            delta < 0 ? -steps : steps,
            null,
            null));
  }

  private List<RemoteInput> key(JsonNode input) {
    String key = input.path("key").asText("");
    int modifiers = input.path("modifiers").asInt(0);
    if (key.isEmpty() || MODIFIER_KEYS.contains(key)) {
      return List.of();
    }
    boolean command = (modifiers & (CTRL | ALT | META)) != 0;
    if (key.codePointCount(0, key.length()) == 1 && !command) {
      return List.of(new RemoteInput("text", null, null, null, null, null, key));
    }
    String name = KEYS.get(key);
    if (name == null && key.length() == 1) {
      name = key.toLowerCase();
    }
    if (name == null || !name.matches("[A-Za-z0-9_]{1,30}")) {
      return List.of();
    }
    List<String> parts = new ArrayList<>();
    if ((modifiers & CTRL) != 0) {
      parts.add("ctrl");
    }
    if ((modifiers & ALT) != 0) {
      parts.add("alt");
    }
    if ((modifiers & META) != 0) {
      parts.add("super");
    }
    if ((modifiers & SHIFT) != 0 && key.length() > 1) {
      parts.add("shift");
    }
    parts.add(name);
    return List.of(new RemoteInput("key", null, null, null, null, String.join("+", parts), null));
  }

  private static int button(String name) {
    return switch (name) {
      case "middle" -> 2;
      case "right" -> 3;
      default -> 1;
    };
  }

  private static double coordinate(JsonNode input, String field) {
    return Math.max(0, Math.min(1, input.path(field).asDouble(0)));
  }
}
