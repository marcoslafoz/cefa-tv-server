package es.cefa.tv.core.aracast.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Genera el {@code main.json} de una plantilla con la misma estructura que la aplicación de
 * escritorio: timelines de fundido, un {@code Stage} y un único elemento a pantalla completa dentro
 * de {@code MainGroup}.
 */
@Component
@RequiredArgsConstructor
public class TemplateBuilder {

  /** Prefijo que el player sustituye por la carpeta de contenidos. */
  static final String CONTENT_PATH = "@content_path@/";

  private final ObjectMapper mapper;

  /** Plantilla con una sola imagen. */
  public String image(String file) {
    ObjectNode shape = baseShape("Texture");
    shape.put("image", CONTENT_PATH + file);
    return wrap("PhotoShape0", shape);
  }

  /**
   * Plantilla de diapositivas.
   *
   * @param files imágenes en orden
   * @param secondsPerSlide segundos que se muestra cada una
   */
  public String slideshow(List<String> files, int secondsPerSlide) {
    ObjectNode shape = baseShape("Slideshow");
    shape.set("image", paths(files));
    shape.set("expiryDate", blanks(files.size()));
    shape.set("startDate", blanks(files.size()));
    shape.put("loop", true);
    shape.put("mantainAspectRatio", false);
    shape.put("fullScreen", true);
    shape.put("imageDuration", secondsPerSlide);
    shape.put("kenBurns", false);
    shape.put("randomOrder", false);
    shape.set("transitionIn", transition());
    shape.set("transitionOut", transition());
    return wrap("PDFShape0", shape);
  }

  /** Plantilla de vídeos en bucle, en el orden dado y sin sonido. */
  public String videos(List<String> files) {
    return videos(files, true);
  }

  /**
   * Plantilla de vídeos en bucle.
   *
   * @param files vídeos en orden
   * @param muted si se emiten sin sonido
   */
  public String videos(List<String> files, boolean muted) {
    ObjectNode shape = baseShape("Video");
    shape.put("volume", muted ? 0 : 100);
    shape.set("video", paths(files));
    shape.put("randomOrder", false);
    shape.set("expiryDate", blanks(files.size()));
    shape.set("startDate", blanks(files.size()));
    shape.put("loop", true);
    shape.put("autoStart", true);
    return wrap("VideoFileShape0", shape);
  }

  /**
   * Plantilla que muestra una página web a pantalla completa.
   *
   * @param url dirección de la página
   * @param interactive si admite ratón y desplazamiento; el teclado en pantalla del player queda
   *     siempre desactivado porque interfiere con el texto del control remoto
   * @param background color de fondo {@code #RRGGBB}; blanco si es nulo
   */
  public String web(String url, boolean interactive, String background) {
    ObjectNode shape = baseShape("HTML5");
    shape.set(
        "background",
        mapper
            .createObjectNode()
            .put("color", (background == null ? "#ffffff" : background) + "ff"));
    shape.put("reactive", interactive);
    shape.put("scrollable", interactive);
    shape.put("keyboard", false);
    shape.put("userstyles", "");
    shape.put("userscripts", "");
    shape.put("url", url);
    return wrap("HTML5Shape0", shape);
  }

  private ObjectNode baseShape(String uiClass) {
    ObjectNode shape = mapper.createObjectNode();
    shape.set(
        "rotation",
        mapper.createObjectNode().put("axis", "Z").put("angle", 0).put("direction", "CCW"));
    shape.set("dimensions", relative("width", "100%", "height", "100%"));
    shape.set("position", relative("x", "0%", "y", "0%"));
    shape.set("background", mapper.createObjectNode().put("color", "#00000000"));
    shape.put("opacity", 255);
    shape.put("ui_class", uiClass);
    return shape;
  }

  private ObjectNode relative(String k1, String v1, String k2, String v2) {
    return mapper.createObjectNode().put("type", "relativeToStage").put(k1, v1).put(k2, v2);
  }

  private ArrayNode paths(List<String> files) {
    ArrayNode array = mapper.createArrayNode();
    files.forEach(f -> array.add(CONTENT_PATH + f));
    return array;
  }

  private ArrayNode blanks(int count) {
    ArrayNode array = mapper.createArrayNode();
    Collections.nCopies(count, "").forEach(array::add);
    return array;
  }

  private ObjectNode transition() {
    return mapper.createObjectNode().put("effect", "fade").put("duration", 1000);
  }

  private String wrap(String shapeId, ObjectNode shape) {
    ArrayNode elements = mapper.createArrayNode();
    elements.add(timeline("timelineFadeIn"));
    elements.add(behaviour("FadeIn", "StartTemplate", 0, 255, "timelineFadeIn"));
    elements.add(timeline("timelineFadeOut"));
    elements.add(behaviour("FadeOut", "StopTemplate", 255, 0, "timelineFadeOut"));

    ObjectNode group = mapper.createObjectNode();
    group.put("ui_class", "Group");
    group.set(
        "dimensions",
        mapper
            .createObjectNode()
            .put("type", "relativeToStage")
            .put("width", "100%")
            .put("height", "100%"));
    group.set(
        "position", mapper.createObjectNode().put("type", "absolute").put("x", "0").put("y", "0"));
    group.put("opacity", 0);
    group.set("behaviours", mapper.createArrayNode().add("FadeIn").add("FadeOut"));
    group.set(
        "textures", mapper.createArrayNode().add(mapper.createObjectNode().set(shapeId, shape)));

    ObjectNode stage = mapper.createObjectNode();
    stage.put("id", "main-stage");
    stage.put("ui_class", "Stage");
    stage.put("color", "#000000ff");
    stage.set(
        "dimensions",
        mapper
            .createObjectNode()
            .put("type", "absolute")
            .put("width", "100%")
            .put("height", "100%"));
    stage.put("hideCursor", true);
    stage.set("signals", mapper.createArrayNode());
    stage.set(
        "textures",
        mapper.createArrayNode().add(mapper.createObjectNode().set("MainGroup", group)));
    elements.add(stage);

    ObjectNode root = mapper.createObjectNode();
    root.set("streamcast-template", elements);
    try {
      return mapper.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo generar la plantilla", e);
    }
  }

  private ObjectNode timeline(String id) {
    return mapper
        .createObjectNode()
        .put("id", id)
        .put("type", "Timeline")
        .put("duration", 10)
        .put("loop", false);
  }

  private ObjectNode behaviour(String id, String signal, int from, int to, String timeline) {
    ObjectNode node = mapper.createObjectNode();
    node.put("id", id);
    node.put("type", "BehaviourOpacity");
    node.put("startSignal", signal);
    node.set("opacity", mapper.createObjectNode().put("initial", from).put("final", to));
    node.set(
        "alpha", mapper.createObjectNode().put("timeline", timeline).put("function", "LINEAR"));
    return node;
  }
}
