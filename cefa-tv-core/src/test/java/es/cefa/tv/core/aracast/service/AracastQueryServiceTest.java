package es.cefa.tv.core.aracast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.dto.PlayerStatus;
import es.cefa.tv.core.aracast.dto.PlayerTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AracastQueryServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final AracastCommandClient client = mock(AracastCommandClient.class);
  private final AracastQueryService service = new AracastQueryService(client);

  @Test
  void mapeaElEstadoDesdeGetVersion() throws Exception {
    when(client.send(eq("10.0.0.1"), eq("Player.getVersion")))
        .thenReturn(
            mapper.readTree(
                """
                {"result":"4.0.4","mac":"00:01","uptime":"70 days","model":"Insignia",
                 "resolutions":{"current":"1920x1080"},
                 "templates":{"current":"CEFA Oficinas","next":"basic_json",
                              "next_time":"2026-10-16 23:59:30","default":"basic_json"},
                 "totalbytes":"60807527424","freebytes":"45483165696",
                 "real_networks":{"if":[{"name":"eth0","ip":"10.0.0.1"},{"name":"enp2s0","ip":""}]}}
                """));
    when(client.send(eq("10.0.0.1"), eq("System.getConfiguration")))
        .thenReturn(
            mapper.readTree(
                """
                {"result":{"clock":{"datetime":"2026-10-16 11:47:46","timezone":"Europe/Madrid"}}}
                """));

    PlayerStatus status = service.getStatus("10.0.0.1");

    assertThat(status.version()).isEqualTo("4.0.4");
    assertThat(status.currentTemplate()).isEqualTo("CEFA Oficinas");
    assertThat(status.ip()).isEqualTo("10.0.0.1");
    assertThat(status.resolution()).isEqualTo("1920x1080");
    assertThat(status.freeBytes()).isEqualTo(45483165696L);
    assertThat(status.playerTime()).isEqualTo("2026-10-16 11:47:46");
    assertThat(status.timezone()).isEqualTo("Europe/Madrid");
  }

  @Test
  void mapeaYOrdenaLasPlantillas() throws Exception {
    when(client.send(eq("10.0.0.1"), eq("Template.getTemplates")))
        .thenReturn(
            mapper.readTree(
                """
                {"action":"Template.getTemplates","result":{"files":{
                  "Zeta.zip":{"name":"Zeta","hash":"h1","size":10,"mtime":1700000000.5,"content":["Video (V0)"]},
                  "alfa.zip":{"name":"alfa","hash":"h2","size":20,"mtime":1700000001.0,"content":[]}}}}
                """));

    List<PlayerTemplate> templates = service.getTemplates("10.0.0.1");

    assertThat(templates).extracting(PlayerTemplate::name).containsExactly("alfa", "Zeta");
    assertThat(templates.get(1).elements()).containsExactly("Video (V0)");
    assertThat(templates.get(1).modifiedAt().getEpochSecond()).isEqualTo(1700000000L);
  }

  @Test
  void descartaLasMiniaturasVaciasDeLosPlayers2x() {
    String empty =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAAAAAAACAIAAAC06etFAAAAAAAAAAACAIAAAC06etF";
    String onePixel =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    assertThat(AracastQueryService.usableThumbnail(empty)).isNull();
    assertThat(AracastQueryService.usableThumbnail(onePixel)).isEqualTo(onePixel);
    assertThat(AracastQueryService.usableThumbnail(null)).isNull();
  }
}
