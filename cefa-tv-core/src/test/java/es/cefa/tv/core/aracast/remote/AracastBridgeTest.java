package es.cefa.tv.core.aracast.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import es.cefa.tv.core.shell.service.ShellSessionService;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AracastBridgeTest {

  private static final String HOST = "10.0.0.5";

  private final UUID id = UUID.randomUUID();
  private AracastRemoteScreenService screens;
  private AracastShellService shells;
  private PlayerSessionRegistry registry;
  private ShellSessionService shellSessions;
  private AracastBridge bridge;
  private AracastPlayerRepository aracasts;

  @BeforeEach
  void setUp() {
    aracasts = mock(AracastPlayerRepository.class);
    AracastPlayer aracast = new AracastPlayer();
    aracast.setPlayerId(id);
    aracast.setHost(HOST);
    when(aracasts.findAll()).thenReturn(List.of(aracast));
    screens = mock(AracastRemoteScreenService.class);
    shells = mock(AracastShellService.class);
    registry = mock(PlayerSessionRegistry.class);
    shellSessions = mock(ShellSessionService.class);
    bridge =
        new AracastBridge(aracasts, screens, shells, registry, shellSessions, new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    bridge.shutdown();
  }

  @Test
  void handlesTheAracastPlayersInTheDatabaseOnly() {
    assertThat(bridge.handles(id)).isTrue();
    assertThat(bridge.handles(UUID.randomUUID())).isFalse();
  }

  @Test
  void isOnlineOnlyAfterAnAnswerRecently() {
    assertThat(bridge.isOnline(id)).isFalse();

    bridge.markSeen(id, HOST);

    assertThat(bridge.isOnline(id)).isTrue();
  }

  @Test
  void remoteStartStreamsFramesToTheViewersUntilTheyLeave() {
    byte[] jpeg = new byte[] {1, 2, 3};
    when(registry.hasViewers(id)).thenReturn(true, true, false);
    when(screens.frame(eq(HOST), anyLong()))
        .thenReturn(Optional.of(new AracastRemoteScreenService.Frame(jpeg, 1)))
        .thenReturn(Optional.empty());

    bridge.onMessage(id, "{\"type\":\"REMOTE_START\",\"fps\":8}");

    ArgumentCaptor<ByteBuffer> frame = ArgumentCaptor.forClass(ByteBuffer.class);
    verify(registry, timeout(3000)).broadcastBinary(eq(id), frame.capture());
    assertThat(frame.getValue().remaining()).isEqualTo(3);
    verify(screens, timeout(3000)).stop(HOST);
  }

  @Test
  void inputIsTranslatedAndInjectedInOrder() {
    bridge.onMessage(
        id, "{\"type\":\"INPUT\",\"kind\":\"MOUSE_DOWN\",\"x\":0.1,\"y\":0.2,\"button\":\"left\"}");
    bridge.onMessage(
        id, "{\"type\":\"INPUT\",\"kind\":\"MOUSE_UP\",\"x\":0.1,\"y\":0.2,\"button\":\"left\"}");
    bridge.onMessage(id, "{\"type\":\"INPUT\",\"kind\":\"KEY_DOWN\",\"key\":\"Enter\"}");

    ArgumentCaptor<RemoteInput> actions = ArgumentCaptor.forClass(RemoteInput.class);
    verify(screens, timeout(3000).times(2)).input(eq(HOST), actions.capture());
    assertThat(actions.getAllValues())
        .extracting(RemoteInput::type)
        .containsExactly("click", "key");
  }

  @Test
  void shellOpenStartsATerminalAndItsOutputReachesTheShellSession() {
    bridge.onMessage(id, "{\"type\":\"SHELL_OPEN\",\"sessionId\":\"s1\",\"cols\":100,\"rows\":30}");

    ArgumentCaptor<AracastShellService.Listener> listener =
        ArgumentCaptor.forClass(AracastShellService.Listener.class);
    verify(shells, timeout(3000))
        .open(eq(id), eq(HOST), eq("s1"), eq(100), eq(30), listener.capture());
    listener.getValue().output("s1", "root@aracast:~# ");
    listener.getValue().exit("s1", 0);
    verify(shellSessions).onPlayerOutput(id, "s1", "root@aracast:~# ");
    verify(shellSessions).onPlayerExit(id, "s1", 0);
  }

  @Test
  void shellInputResizeAndCloseAreForwarded() {
    bridge.onMessage(id, "{\"type\":\"SHELL_INPUT\",\"sessionId\":\"s1\",\"data\":\"ls\\r\"}");
    bridge.onMessage(
        id, "{\"type\":\"SHELL_RESIZE\",\"sessionId\":\"s1\",\"cols\":120,\"rows\":40}");
    bridge.onMessage(id, "{\"type\":\"SHELL_CLOSE\",\"sessionId\":\"s1\"}");

    verify(shells).input(id, "ls\r");
    verify(shells).resize(id, 120, 40);
    verify(shells).close(id);
  }

  @Test
  void aFailedShellOpenTellsTheSessionItEnded() {
    org.mockito.Mockito.doThrow(new RuntimeException("sin SSH"))
        .when(shells)
        .open(any(), any(), any(), anyInt(), anyInt(), any());

    bridge.onMessage(id, "{\"type\":\"SHELL_OPEN\",\"sessionId\":\"s2\"}");

    verify(shellSessions, timeout(3000)).onPlayerExit(id, "s2", 1);
  }

  @Test
  void forgettingAPlayerClosesWhatItHadOpen() {
    assertThat(bridge.handles(id)).isTrue();
    when(aracasts.findAll()).thenReturn(List.of());

    bridge.forget(id);
    bridge.refresh();

    verify(shells).close(id);
    assertThat(bridge.handles(id)).isFalse();
  }
}
