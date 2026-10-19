package es.cefa.tv.core.aracast.remote;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AracastInputMapperTest {

  private final ObjectMapper json = new ObjectMapper();
  private final AracastInputMapper mapper = new AracastInputMapper();

  private List<RemoteInput> map(String input) throws Exception {
    JsonNode node = json.readTree(input);
    return mapper.map(node);
  }

  @Test
  void aClickIsSentWhenTheButtonIsReleased() throws Exception {
    assertThat(map("{\"kind\":\"MOUSE_DOWN\",\"x\":0.2,\"y\":0.3,\"button\":\"right\"}")).isEmpty();

    List<RemoteInput> actions =
        map("{\"kind\":\"MOUSE_UP\",\"x\":0.25,\"y\":0.5,\"button\":\"right\"}");

    assertThat(actions).hasSize(1);
    RemoteInput click = actions.get(0);
    assertThat(click.type()).isEqualTo("click");
    assertThat(click.x()).isEqualTo(0.25);
    assertThat(click.y()).isEqualTo(0.5);
    assertThat(click.button()).isEqualTo(3);
    assertThat(click.count()).isEqualTo(1);
  }

  @Test
  void aReleaseWithoutAPressAndMouseMovesAreIgnored() throws Exception {
    assertThat(map("{\"kind\":\"MOUSE_UP\",\"x\":0.1,\"y\":0.1,\"button\":\"left\"}")).isEmpty();
    assertThat(map("{\"kind\":\"MOUSE_MOVE\",\"x\":0.1,\"y\":0.1}")).isEmpty();
  }

  @Test
  void theWheelBecomesScrollStepsWithTheRightSign() throws Exception {
    RemoteInput down = map("{\"kind\":\"MOUSE_WHEEL\",\"x\":0.5,\"y\":0.5,\"deltaY\":300}").get(0);
    RemoteInput up = map("{\"kind\":\"MOUSE_WHEEL\",\"x\":0.5,\"y\":0.5,\"deltaY\":-40}").get(0);

    assertThat(down.type()).isEqualTo("scroll");
    assertThat(down.count()).isEqualTo(3);
    assertThat(up.count()).isEqualTo(-1);
    assertThat(map("{\"kind\":\"MOUSE_WHEEL\",\"x\":0.5,\"y\":0.5,\"deltaY\":0}")).isEmpty();
  }

  @Test
  void aPrintableKeyIsTypedAsText() throws Exception {
    RemoteInput action = map("{\"kind\":\"KEY_DOWN\",\"key\":\"A\",\"modifiers\":8}").get(0);

    assertThat(action.type()).isEqualTo("text");
    assertThat(action.text()).isEqualTo("A");
  }

  @Test
  void specialKeysAndShortcutsUseX11Names() throws Exception {
    assertThat(map("{\"kind\":\"KEY_DOWN\",\"key\":\"Enter\"}").get(0).key()).isEqualTo("Return");
    assertThat(map("{\"kind\":\"KEY_DOWN\",\"key\":\"ArrowLeft\"}").get(0).key()).isEqualTo("Left");
    assertThat(map("{\"kind\":\"KEY_DOWN\",\"key\":\"PageDown\"}").get(0).key()).isEqualTo("Next");
    assertThat(map("{\"kind\":\"KEY_DOWN\",\"key\":\"a\",\"modifiers\":2}").get(0).key())
        .isEqualTo("ctrl+a");
    assertThat(map("{\"kind\":\"KEY_DOWN\",\"key\":\"Tab\",\"modifiers\":10}").get(0).key())
        .isEqualTo("ctrl+shift+Tab");
  }

  @Test
  void modifierKeysKeyUpAndUnknownKeysAreIgnored() throws Exception {
    assertThat(map("{\"kind\":\"KEY_DOWN\",\"key\":\"Shift\"}")).isEmpty();
    assertThat(map("{\"kind\":\"KEY_UP\",\"key\":\"a\"}")).isEmpty();
    assertThat(map("{\"kind\":\"KEY_DOWN\",\"key\":\"Dead\"}")).isEmpty();
  }

  @Test
  void textIsPassedThroughAndTrimmed() throws Exception {
    RemoteInput action = map("{\"kind\":\"TEXT\",\"text\":\"hola\"}").get(0);
    assertThat(action.text()).isEqualTo("hola");

    String long600 = "x".repeat(600);
    assertThat(map("{\"kind\":\"TEXT\",\"text\":\"" + long600 + "\"}").get(0).text()).hasSize(500);
    assertThat(map("{\"kind\":\"TEXT\",\"text\":\"\"}")).isEmpty();
  }
}
