package es.cefa.tv.core.aracast.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.config.AracastProperties;
import es.cefa.tv.core.aracast.exception.AracastException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AracastCommandClientTest {

  private static AracastCommandClient client(int port) {
    return new AracastCommandClient(
        new AracastProperties(
            port, 2000, 5481, "user", "", 2000, "root", java.util.List.of(), 2000),
        new ObjectMapper());
  }

  @Test
  void enviaUnaLineaJsonYDevuelveElNodoCommand() throws Exception {
    try (FakeAracastServer server =
        new FakeAracastServer(
            req -> "{\"command\":{\"result\":\"4.0.4\",\"action\":\"Player.getVersion\"}}")) {
      JsonNode command = client(server.port()).send("127.0.0.1", "Player.getVersion");

      assertThat(command.path("result").asText()).isEqualTo("4.0.4");
      assertThat(server.received)
          .containsExactly("{\"command\":{\"action\":\"Player.getVersion\"}}");
    }
  }

  @Test
  void incluyeLosParametrosDentroDeCommand() throws Exception {
    try (FakeAracastServer server =
        new FakeAracastServer(req -> "{\"command\":{\"action\":\"x\",\"result\":\"ok\"}}")) {
      client(server.port())
          .send("127.0.0.1", "Template.getContent", Map.of("files", java.util.List.of("a.mp4")));

      assertThat(server.received.get(0)).contains("\"files\":[\"a.mp4\"]");
    }
  }

  @Test
  void lanzaExcepcionSiElPlayerDevuelveFail() throws Exception {
    try (FakeAracastServer server =
        new FakeAracastServer(req -> "{\"command\":{\"action\":\"x\",\"result\":\"fail\"}}")) {
      assertThatThrownBy(() -> client(server.port()).send("127.0.0.1", "Player.loadEvents"))
          .isInstanceOf(AracastException.class)
          .hasMessageContaining("rechazó");
    }
  }

  @Test
  void lanzaExcepcionSiNoHayNadieEscuchando() throws Exception {
    int freePort;
    try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
      freePort = s.getLocalPort();
    }
    assertThatThrownBy(() -> client(freePort).send("127.0.0.1", "Player.getVersion"))
        .isInstanceOf(AracastException.class);
  }
}
