package es.cefa.tv.core.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.shell.model.ShellCloseReason;
import es.cefa.tv.core.shell.service.ShellAuditService;
import es.cefa.tv.core.shell.service.ShellSessionService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Pruebas de la lógica de sesiones de terminal remota sin infraestructura. */
class ShellSessionServiceTest {

  private final UUID playerId = UUID.randomUUID();
  private final UUID auditId = UUID.randomUUID();
  private final AuthenticatedUser admin =
      new AuthenticatedUser(
          UUID.randomUUID(),
          "root",
          "Root",
          java.util.List.of(),
          true,
          java.util.EnumSet.allOf(Permission.class),
          Map.of(),
          Map.of(),
          null,
          "LOCAL");

  private PlayerRepository players;
  private PlayerSessionRegistry registry;
  private ShellAuditService audits;
  private ShellSessionService service;
  private Player player;

  @BeforeEach
  void setUp() {
    players = mock(PlayerRepository.class);
    registry = mock(PlayerSessionRegistry.class);
    audits = mock(ShellAuditService.class);
    CoreProperties properties =
        new CoreProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new CoreProperties.Shell(900, 7200),
            null,
            null);
    service = new ShellSessionService(players, registry, audits, properties, new ObjectMapper());
    player = new Player();
    player.setId(playerId);
    player.setName("Recepción");
    when(players.findById(playerId)).thenReturn(Optional.of(player));
    when(registry.isConnected(playerId)).thenReturn(true);
    when(registry.sendToPlayer(eq(playerId), any())).thenReturn(true);
    when(audits.open(eq(playerId), any(), any(), any())).thenReturn(auditId);
  }

  private WebSocketSession viewer(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    when(session.isOpen()).thenReturn(true);
    when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
    return session;
  }

  private String lastTextSentTo(WebSocketSession session) throws Exception {
    ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
    return captor.getValue().getPayload();
  }

  @Test
  void opensASessionAndAnnouncesItToBothSides() throws Exception {
    WebSocketSession viewer = viewer("v1");

    service.open(playerId, viewer, admin, 120, 40);

    verify(registry).sendToPlayer(eq(playerId), contains("\"type\":\"SHELL_OPEN\""));
    verify(registry).sendToPlayer(eq(playerId), contains("\"cols\":120"));
    assertThat(lastTextSentTo(viewer)).contains("OPENED");
    assertThat(service.hasSession(playerId)).isTrue();
  }

  @Test
  void rejectsAnOfflinePlayer() throws Exception {
    when(registry.isConnected(playerId)).thenReturn(false);
    WebSocketSession viewer = viewer("v1");

    service.open(playerId, viewer, admin, 80, 24);

    assertThat(lastTextSentTo(viewer)).contains("PLAYER_OFFLINE");
    assertThat(service.hasSession(playerId)).isFalse();
  }

  @Test
  void rejectsAPlayerWithTheShellDisabled() throws Exception {
    player.setSystemInfo(Map.of("shellEnabled", false));
    WebSocketSession viewer = viewer("v1");

    service.open(playerId, viewer, admin, 80, 24);

    assertThat(lastTextSentTo(viewer)).contains("SHELL_DISABLED");
    verify(audits, never()).open(any(), any(), any(), any());
  }

  @Test
  void allowsOnlyOneSessionPerPlayer() throws Exception {
    service.open(playerId, viewer("v1"), admin, 80, 24);
    WebSocketSession second = viewer("v2");

    service.open(playerId, second, admin, 80, 24);

    assertThat(lastTextSentTo(second)).contains("SHELL_BUSY");
  }

  @Test
  void ignoresInputFromAViewerThatDoesNotOwnTheSession() {
    service.open(playerId, viewer("v1"), admin, 80, 24);

    service.input(playerId, "intruso", "ls\n");

    verify(registry, never()).sendToPlayer(eq(playerId), contains("SHELL_INPUT"));
  }

  @Test
  void closesIdleSessionsAndAuditsThem() {
    service.open(playerId, viewer("v1"), admin, 80, 24);
    service.input(playerId, "v1", "ls\n");

    service.expireSessions(Instant.now().plusSeconds(899));
    assertThat(service.hasSession(playerId)).isTrue();

    service.expireSessions(Instant.now().plusSeconds(901));
    assertThat(service.hasSession(playerId)).isFalse();
    verify(registry).sendToPlayer(eq(playerId), contains("SHELL_CLOSE"));
    verify(audits).close(eq(auditId), eq(3L), eq(0L), eq(ShellCloseReason.IDLE_TIMEOUT));
  }

  @Test
  void closesSessionsThatExceedTheMaximumDuration() {
    service.open(playerId, viewer("v1"), admin, 80, 24);

    service.expireSessions(Instant.now().plusSeconds(7201));

    assertThat(service.hasSession(playerId)).isFalse();
    verify(audits).close(eq(auditId), anyLong(), anyLong(), eq(ShellCloseReason.MAX_DURATION));
  }

  @Test
  void countsTrafficAndClosesWhenThePlayerDisconnects() throws Exception {
    WebSocketSession viewer = viewer("v1");
    service.open(playerId, viewer, admin, 80, 24);
    String sessionId = sessionIdOf(viewer);

    service.input(playerId, "v1", "echo hola\n");
    service.onPlayerOutput(playerId, sessionId, "hola\n");
    service.onPlayerDisconnected(playerId);

    assertThat(service.hasSession(playerId)).isFalse();
    verify(audits).close(eq(auditId), eq(10L), eq(5L), eq(ShellCloseReason.PLAYER_DISCONNECTED));
  }

  private String sessionIdOf(WebSocketSession viewer) throws Exception {
    String opened = lastTextSentTo(viewer);
    return new ObjectMapper().readTree(opened).get("sessionId").asText();
  }
}
