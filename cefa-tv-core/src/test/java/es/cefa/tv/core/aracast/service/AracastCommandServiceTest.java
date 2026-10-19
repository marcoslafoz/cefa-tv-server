package es.cefa.tv.core.aracast.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.player.model.CommandType;
import es.cefa.tv.core.shared.exception.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AracastCommandServiceTest {

  private final UUID id = UUID.randomUUID();
  private AracastCommandClient commands;
  private AracastCommandService service;

  @BeforeEach
  void setUp() {
    commands = mock(AracastCommandClient.class);
    AracastPlayerRepository aracasts = mock(AracastPlayerRepository.class);
    AracastPlayer aracast = new AracastPlayer();
    aracast.setPlayerId(id);
    aracast.setHost("10.0.0.5");
    when(aracasts.findById(id)).thenReturn(Optional.of(aracast));
    service =
        new AracastCommandService(
            aracasts, commands, mock(es.cefa.tv.core.aracast.remote.AracastBridge.class));
  }

  @Test
  void restartRebootsTheEquipment() {
    service.execute(id, CommandType.RESTART);

    verify(commands).send("10.0.0.5", "System.reboot");
  }

  @Test
  void otherCommandsAreNotAvailable() {
    assertThatThrownBy(() -> service.execute(id, CommandType.RELOAD))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no está disponible");
    verify(commands, never()).send("10.0.0.5", "System.reboot");
  }

  @Test
  void aPlayerThatIsNotAnAracastIsRejected() {
    assertThatThrownBy(() -> service.hostOf(UUID.randomUUID()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no es un Aracast");
  }
}
