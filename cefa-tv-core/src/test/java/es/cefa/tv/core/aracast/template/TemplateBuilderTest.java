package es.cefa.tv.core.aracast.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateBuilderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final TemplateBuilder builder = new TemplateBuilder(mapper);

  private JsonNode shape(String json, String id) throws Exception {
    JsonNode stage = mapper.readTree(json).path("streamcast-template").get(4);
    assertThat(stage.path("ui_class").asText()).isEqualTo("Stage");
    return stage.path("textures").get(0).path("MainGroup").path("textures").get(0).path(id);
  }

  @Test
  void imagenUnica() throws Exception {
    JsonNode s = shape(builder.image("a_123.png"), "PhotoShape0");

    assertThat(s.path("ui_class").asText()).isEqualTo("Texture");
    assertThat(s.path("image").asText()).isEqualTo("@content_path@/a_123.png");
  }

  @Test
  void diapositivasConDuracion() throws Exception {
    JsonNode s = shape(builder.slideshow(List.of("p0.png", "p1.png"), 8), "PDFShape0");

    assertThat(s.path("ui_class").asText()).isEqualTo("Slideshow");
    assertThat(s.path("image")).hasSize(2);
    assertThat(s.path("expiryDate")).hasSize(2);
    assertThat(s.path("imageDuration").asInt()).isEqualTo(8);
  }

  @Test
  void videosYWeb() throws Exception {
    JsonNode v = shape(builder.videos(List.of("v_1.mp4")), "VideoFileShape0");
    JsonNode w = shape(builder.web("https://example.org", true, "#112233"), "HTML5Shape0");

    assertThat(v.path("video").get(0).asText()).isEqualTo("@content_path@/v_1.mp4");
    assertThat(v.path("loop").asBoolean()).isTrue();
    assertThat(w.path("url").asText()).isEqualTo("https://example.org");
    assertThat(w.path("reactive").asBoolean()).isTrue();
    assertThat(w.path("scrollable").asBoolean()).isTrue();
    assertThat(w.path("keyboard").asBoolean()).isTrue();
    assertThat(w.path("background").path("color").asText()).isEqualTo("#112233ff");
    JsonNode defaultBg = shape(builder.web("https://example.org", true, null), "HTML5Shape0");
    assertThat(defaultBg.path("background").path("color").asText()).isEqualTo("#ffffffff");
  }
}
