package es.cefa.tv.core.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.preview.service.PreviewFrame;
import es.cefa.tv.core.preview.service.PreviewService;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/** Pruebas de la vista previa: suscriptores, intervalos, caducidad, memoria y retransmisión. */
class PreviewServiceTest {

  private final UUID playerA = UUID.randomUUID();
  private final UUID playerB = UUID.randomUUID();
  private final AuthenticatedUser admin =
      new AuthenticatedUser(
          UUID.randomUUID(),
          "root",
          "Root",
          java.util.List.of(),
          true,
          EnumSet.allOf(Permission.class),
          Map.of(),
          Map.of(),
          null,
          "LOCAL");

  private PlayerSessionRegistry registry;
  private PlayerRepository players;
  private PreviewService service;
  private org.springframework.context.ApplicationEventPublisher events;

  @BeforeEach
  void setUp() {
    registry = mock(PlayerSessionRegistry.class);
    players = mock(PlayerRepository.class);
    when(players.findById(any(UUID.class)))
        .thenAnswer(
            invocation -> {
              Player player = new Player();
              player.setId(invocation.getArgument(0));
              return Optional.of(player);
            });
    events = org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);
    service =
        new PreviewService(
            registry, players, new ObjectMapper(), events, new SimpleMeterRegistry());
  }

  private WebSocketSession session(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    return session;
  }

  private AuthenticatedUser scheduler(UUID groupId, AccessLevel level) {
    return new AuthenticatedUser(
        UUID.randomUUID(),
        "prog",
        "Prog",
        java.util.List.of(),
        false,
        EnumSet.of(Permission.PLAYER_VIEW),
        Map.of(groupId, level),
        Map.of(),
        null,
        "LOCAL");
  }

  @Test
  void theFirstSubscriberStartsTheCaptureAndTheLastOneStopsIt() {
    WebSocketSession first = session("s1");
    WebSocketSession second = session("s2");

    service.subscribe(first, admin, List.of(playerA), 10);
    service.subscribe(second, admin, List.of(playerA), 10);

    ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
    verify(registry, times(1)).sendToPlayer(eq(playerA), sent.capture());
    assertThat(sent.getValue())
        .contains("PREVIEW_START")
        .contains("\"intervalSec\":10")
        .contains("\"maxWidth\":320")
        .contains("\"quality\":45");
    assertThat(service.subscriberCount(playerA)).isEqualTo(2);

    service.unsubscribe("s1", List.of(playerA));
    verify(registry, never()).sendToPlayer(eq(playerA), contains("PREVIEW_STOP"));
    service.close("s2");
    verify(registry).sendToPlayer(eq(playerA), contains("PREVIEW_STOP"));
    assertThat(service.subscriberCount(playerA)).isZero();
  }

  @Test
  void theShortestRequestedIntervalWinsAndIsRelaxedWhenItLeaves() {
    service.subscribe(session("slow"), admin, List.of(playerA), 30);
    service.subscribe(session("fast"), admin, List.of(playerA), 5);

    verify(registry).sendToPlayer(eq(playerA), contains("\"intervalSec\":30"));
    verify(registry).sendToPlayer(eq(playerA), contains("\"intervalSec\":5"));

    service.close("fast");
    verify(registry, times(2)).sendToPlayer(eq(playerA), contains("\"intervalSec\":30"));
  }

  @Test
  void theIntervalIsClampedAndDefaultsToTen() {
    service.subscribe(session("a"), admin, List.of(playerA), 1);
    verify(registry).sendToPlayer(eq(playerA), contains("\"intervalSec\":5"));
    service.subscribe(session("b"), admin, List.of(playerB), 500);
    verify(registry).sendToPlayer(eq(playerB), contains("\"intervalSec\":60"));
    UUID third = UUID.randomUUID();
    service.subscribe(session("c"), admin, List.of(third), null);
    verify(registry).sendToPlayer(eq(third), contains("\"intervalSec\":10"));
  }

  @Test
  void onlyPlayersTheUserCanSeeAreAccepted() {
    UUID groupId = UUID.randomUUID();
    Player visible = new Player();
    visible.setId(playerA);
    visible.setGroupId(groupId);
    when(players.findById(playerA)).thenReturn(Optional.of(visible));
    Player hidden = new Player();
    hidden.setId(playerB);
    hidden.setGroupId(UUID.randomUUID());
    when(players.findById(playerB)).thenReturn(Optional.of(hidden));
    UUID unknown = UUID.randomUUID();
    when(players.findById(unknown)).thenReturn(Optional.empty());

    List<UUID> accepted =
        service.subscribe(
            session("s"),
            scheduler(groupId, AccessLevel.VIEW),
            List.of(playerA, playerB, unknown),
            10);

    assertThat(accepted).containsExactly(playerA);
    verify(registry).sendToPlayer(eq(playerA), contains("PREVIEW_START"));
    verify(registry, never()).sendToPlayer(eq(playerB), any());
  }

  @Test
  void aSubscriberIsLimitedToFortyEightPlayers() {
    List<UUID> many = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      many.add(UUID.randomUUID());
    }

    List<UUID> accepted = service.subscribe(session("s"), admin, many, 10);

    assertThat(accepted).hasSize(PreviewService.MAX_PLAYERS_PER_SUBSCRIBER);
  }

  @Test
  void subscriptionsExpireWithoutRenewalAndRenewalKeepsThem() throws Exception {
    WebSocketSession idle = session("idle");
    WebSocketSession renewed = session("renewed");
    Instant before = Instant.now();
    service.subscribe(idle, admin, List.of(playerA), 10);
    service.subscribe(renewed, admin, List.of(playerB), 10);

    service.expireStale(before.plusSeconds(29));
    verify(registry, never()).sendToPlayer(any(), contains("PREVIEW_STOP"));

    Thread.sleep(800);
    service.subscribe(renewed, admin, List.of(playerB), 10);
    service.expireStale(before.plusMillis(30_300));

    verify(registry).sendToPlayer(eq(playerA), contains("PREVIEW_STOP"));
    verify(registry, never()).sendToPlayer(eq(playerB), contains("PREVIEW_STOP"));
    verify(idle).sendMessage(any(TextMessage.class));
    assertThat(service.subscriberCount(playerB)).isEqualTo(1);
  }

  @Test
  void framesAreRelayedWithThePlayerIdPrefixOnlyToItsSubscribers() throws Exception {
    WebSocketSession watching = session("watching");
    WebSocketSession other = session("other");
    service.subscribe(watching, admin, List.of(playerA), 10);
    service.subscribe(other, admin, List.of(playerB), 10);
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

    service.onFrame(playerA, jpeg);

    ArgumentCaptor<WebSocketMessage<?>> sent = ArgumentCaptor.forClass(WebSocketMessage.class);
    verify(watching).sendMessage(sent.capture());
    ByteBuffer payload = ((BinaryMessage) sent.getValue()).getPayload();
    byte[] bytes = new byte[payload.remaining()];
    payload.get(bytes);
    assertThat(new String(bytes, 0, 36, StandardCharsets.US_ASCII)).isEqualTo(playerA.toString());
    assertThat(java.util.Arrays.copyOfRange(bytes, 36, bytes.length)).isEqualTo(jpeg);
    verify(other, never()).sendMessage(any());
    PreviewFrame cached = service.latest(playerA);
    assertThat(cached.jpeg()).isEqualTo(jpeg);
    assertThat(service.previewAt(playerA)).isNotNull();
    assertThat(service.latest(playerB)).isNull();
  }

  @Test
  void oversizedFramesAndFramesNobodyWatchesAreDropped() throws Exception {
    WebSocketSession watching = session("watching");
    service.subscribe(watching, admin, List.of(playerA), 10);

    service.onFrame(playerA, new byte[PreviewService.MAX_FRAME_BYTES + 1]);
    service.onFrame(playerB, new byte[100]);
    service.onFrame(playerA, null);

    verify(watching, never()).sendMessage(any());
    assertThat(service.latest(playerA)).isNull();
    assertThat(service.latest(playerB)).isNull();
    service.onFrame(playerA, new byte[PreviewService.MAX_FRAME_BYTES]);
    assertThat(service.latest(playerA)).isNotNull();
  }

  @Test
  void onlyTheLatestFramePerPlayerIsKeptAndMemoryIsCapped() {
    service.subscribe(session("s"), admin, List.of(playerA), 10);
    service.onFrame(playerA, new byte[] {1});
    service.onFrame(playerA, new byte[] {2, 2});
    assertThat(service.latest(playerA).jpeg()).containsExactly(2, 2);

    List<UUID> ids = new ArrayList<>();
    for (int viewer = 0; viewer < 12; viewer++) {
      List<UUID> batch = new ArrayList<>();
      for (int i = 0; i < PreviewService.MAX_PLAYERS_PER_SUBSCRIBER; i++) {
        batch.add(UUID.randomUUID());
      }
      ids.addAll(batch);
      service.subscribe(session("v" + viewer), admin, batch, 10);
    }
    ids.forEach(id -> service.onFrame(id, new byte[PreviewService.MAX_FRAME_BYTES]));

    long stored = ids.stream().filter(id -> service.latest(id) != null).count();
    assertThat(stored).isLessThanOrEqualTo(PreviewService.MAX_CACHED_FRAMES);
    assertThat(stored * PreviewService.MAX_FRAME_BYTES)
        .isLessThanOrEqualTo(PreviewService.MAX_CACHED_BYTES);
    assertThat(service.latest(ids.get(ids.size() - 1))).isNotNull();
  }

  @Test
  void aPlayerThatReconnectsWithWatchersIsToldToStartAgain() {
    service.subscribe(session("s"), admin, List.of(playerA), 20);

    service.onPlayerHello(playerA);
    service.onPlayerHello(playerB);

    verify(registry, times(2)).sendToPlayer(eq(playerA), contains("\"intervalSec\":20"));
    verify(registry, never()).sendToPlayer(eq(playerB), any());
  }

  @Test
  void aSubscriberThatCannotReceiveIsDroppedAndTheCaptureStops() throws Exception {
    WebSocketSession broken = session("broken");
    doThrow(new IOException("cerrada")).when(broken).sendMessage(any());
    service.subscribe(broken, admin, List.of(playerA), 10);

    service.onFrame(playerA, new byte[] {1, 2, 3});

    verify(registry).sendToPlayer(eq(playerA), contains("PREVIEW_STOP"));
    assertThat(service.subscriberCount(playerA)).isZero();
  }
}
