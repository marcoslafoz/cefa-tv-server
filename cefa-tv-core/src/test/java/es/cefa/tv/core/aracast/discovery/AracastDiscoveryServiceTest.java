package es.cefa.tv.core.aracast.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.cefa.tv.core.aracast.dto.DiscoveredPlayer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AracastDiscoveryServiceTest {

  @Test
  void interpretaLaRespuestaDelPlayer() {
    DiscoveredPlayer p =
        AracastDiscoveryService.parse(
                "10.21.34.33", "ARAPROD03\n00:01:80:89:55:0F\nAraCastPlayerDual v2.6.19")
            .orElseThrow();

    assertThat(p)
        .isEqualTo(
            new DiscoveredPlayer(
                "10.21.34.33", "ARAPROD03", "00:01:80:89:55:0f", "AraCastPlayerDual", "2.6.19"));
    assertThat(AracastDiscoveryService.parse("10.0.0.1", "basura")).isEmpty();
  }

  @Test
  void barreYRecogeLasRespuestas() throws Exception {
    try (DatagramSocket fake = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
      Thread responder =
          new Thread(
              () -> {
                try {
                  byte[] buf = new byte[16];
                  DatagramPacket in = new DatagramPacket(buf, buf.length);
                  fake.receive(in);
                  byte[] reply =
                      "PLAYER\n00:11:22:33:44:55\nAraCastPlayerInsignia v4.0.4"
                          .getBytes(StandardCharsets.UTF_8);
                  fake.send(new DatagramPacket(reply, reply.length, in.getAddress(), in.getPort()));
                } catch (Exception ignored) {
                  return;
                }
              });
      responder.start();
      AracastDiscoveryService service = new AracastDiscoveryService(fake.getLocalPort(), 500, 100);

      List<DiscoveredPlayer> found = service.scan(List.of("127.0.0.1"));

      assertThat(found).extracting(DiscoveredPlayer::version).containsExactly("4.0.4");
    }
  }

  @Test
  void limitaElNumeroDeDirecciones() {
    AracastDiscoveryService service = new AracastDiscoveryService(30333, 100, 300);

    assertThat(service.expand(List.of("10.0.0.0/24", "10.0.0.5"))).hasSize(254);
    assertThatThrownBy(() -> service.expand(List.of("10.0.0.0/23")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
