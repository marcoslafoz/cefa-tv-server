package es.cefa.tv.core.aracast.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.client.AracastFtpClient;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class AracastNetworkServiceTest {

  private static final String HOST = "10.0.0.5";

  private AracastCommandClient commands;
  private AracastFtpClient ftp;
  private AracastPlayerRepository aracasts;
  private AracastNetworkService service;

  @BeforeEach
  void setUp() throws Exception {
    commands = mock(AracastCommandClient.class);
    ftp = mock(AracastFtpClient.class);
    aracasts = mock(AracastPlayerRepository.class);
    TransactionTemplate transaction = mock(TransactionTemplate.class);
    ObjectMapper mapper = new ObjectMapper();
    when(commands.send(HOST, "System.getConfiguration"))
        .thenReturn(
            mapper.readTree(
                """
                {"result":{"hostname":"ARA1",
                 "networks":{"eth0":{"type":"static","ip":"10.0.0.5","netmask":"255.255.255.0",
                   "gateway":"10.0.0.1","dns":{"primary":"10.0.0.2","secondary":""}}},
                 "real_networks":{"if":[{"ip":"10.0.0.5","netmask":"255.255.255.0","gateway":"10.0.0.1"}],
                   "dns":["10.0.0.2"]}}}
                """));
    service =
        new AracastNetworkService(
            commands,
            ftp,
            new AracastLocks(),
            aracasts,
            mock(PlayerRepository.class),
            mapper,
            transaction);
  }

  private static NetworkChangeRequest request(String ip, String hostname, boolean reboot) {
    return new NetworkChangeRequest(
        hostname, ip, "255.255.255.0", "10.0.0.1", "10.0.0.2", null, reboot);
  }

  @Test
  void readsTheSavedAndTheActiveNetwork() {
    NetworkInfo info = service.get(HOST);

    assertThat(info.configured().ip()).isEqualTo("10.0.0.5");
    assertThat(info.configured().type()).isEqualTo("static");
    assertThat(info.effective().dnsPrimary()).isEqualTo("10.0.0.2");
    assertThat(info.effective().hostname()).isEqualTo("ARA1");
  }

  @Test
  void rejectsAGatewayOutsideTheSubnet() {
    NetworkChangeRequest bad =
        new NetworkChangeRequest(
            "ARA1", "10.0.0.9", "255.255.255.0", "10.9.9.1", "10.0.0.2", null, true);

    assertThatThrownBy(() -> service.apply(HOST, bad))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("misma subred");
    verify(ftp, never()).upload(any(), any(), any());
  }

  @Test
  void rejectsARequestWithoutChanges() {
    assertThatThrownBy(() -> service.apply(HOST, request("10.0.0.5", "ARA1", false)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("ningún cambio");
  }

  @Test
  void refusesAnAddressAlreadyUsedByAnotherAracast() {
    when(aracasts.findByHostIgnoreCase("10.0.0.9")).thenReturn(Optional.of(new AracastPlayer()));

    assertThatThrownBy(() -> service.apply(HOST, request("10.0.0.9", "ARA1", true)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("dado de alta con la IP");
  }

  @Test
  void refusesAnAddressWhereSomethingAlreadyAnswers() {
    when(commands.isReachable(eq("10.0.0.9"), anyInt())).thenReturn(true);

    assertThatThrownBy(() -> service.apply(HOST, request("10.0.0.9", "ARA1", true)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("respondiendo");
  }

  @Test
  void changingTheAddressWritesTheConfigAndRebootsEvenIfNotAsked() {
    NetworkChangeResult result = service.apply(HOST, request("10.0.0.9", "ARA1", false));

    verify(ftp).upload(eq(HOST), eq("ctv-network.cfg"), any());
    verify(commands).send(HOST, "System.setConfiguration", Map.of("file", "ctv-network.cfg"));
    verify(commands).send(HOST, "System.reboot");
    assertThat(result.ipChanged()).isTrue();
    assertThat(result.rebooting()).isTrue();
    assertThat(result.host()).isEqualTo("10.0.0.9");
  }

  @Test
  void renamingAloneDoesNotReboot() {
    NetworkChangeResult result = service.apply(HOST, request("10.0.0.5", "ARA2", false));

    assertThat(result.ipChanged()).isFalse();
    assertThat(result.rebooting()).isFalse();
    verify(commands, never()).send(HOST, "System.reboot");
    verify(commands, never()).send(eq(HOST), eq("System.reboot"), anyMap());
  }
}
