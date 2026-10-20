package es.cefa.tv.core.aracast.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.plan.AracastPlan;
import es.cefa.tv.core.aracast.plan.AracastPlanner;
import es.cefa.tv.core.aracast.remote.AracastRemoteSessionEvent;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.aracast.service.AracastPlayerService;
import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.delivery.service.DeliveryService;
import es.cefa.tv.core.manifest.model.ManifestDocument;
import es.cefa.tv.core.manifest.model.ManifestDocument.AssetEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.FileEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.ItemEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.PlaylistEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.ScheduleEntry;
import es.cefa.tv.core.manifest.model.PlayerManifest;
import es.cefa.tv.core.manifest.service.ManifestService;
import es.cefa.tv.core.player.model.IdleLogo;
import es.cefa.tv.core.player.model.IdleLogoSource;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.IdleLogoResolver;
import es.cefa.tv.core.shared.util.Hashes;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AracastDeliveryServiceTest {

  private static final String PLAYLIST = "11111111-1111-1111-1111-111111111111";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

  private final UUID id = UUID.randomUUID();
  private Player player;
  private AracastPlayer aracast;
  private ManifestService manifests;
  private AracastPublisher publisher;
  private AracastPlayerService playerService;
  private DeliveryService deliveries;
  private AracastCommandClient commands;
  private AuditService audit;
  private AracastDeliveryService service;

  @BeforeEach
  void setUp() {
    player = new Player();
    player.setId(id);
    player.setName("Pantalla");
    player.setDesiredVersion(3);
    player.setAppliedVersion(2);
    aracast = new AracastPlayer();
    aracast.setPlayerId(id);
    aracast.setHost("10.0.0.5");
    PlayerRepository players = mock(PlayerRepository.class);
    when(players.findById(id)).thenReturn(Optional.of(player));
    AracastPlayerRepository aracasts = mock(AracastPlayerRepository.class);
    when(aracasts.findById(id)).thenReturn(Optional.of(aracast));
    manifests = mock(ManifestService.class);
    publisher = mock(AracastPublisher.class);
    when(publisher.publish(anyString(), any(), any()))
        .thenReturn(new AracastPublisher.Result(2, 1, 1, 0));
    playerService = mock(AracastPlayerService.class);
    deliveries = mock(DeliveryService.class);
    commands = mock(AracastCommandClient.class);
    when(commands.isReachable(anyString(), anyInt())).thenReturn(true);
    audit = mock(AuditService.class);
    IdleLogoResolver idleLogos = mock(IdleLogoResolver.class);
    when(idleLogos.effective(any(Player.class)))
        .thenReturn(new IdleLogoResolver.Effective(IdleLogo.CEFA, IdleLogoSource.DEFAULT));
    service =
        new AracastDeliveryService(
            players,
            aracasts,
            manifests,
            publisher,
            playerService,
            deliveries,
            commands,
            MAPPER,
            idleLogos,
            audit);
  }

  private ManifestDocument document(boolean withSchedule, boolean emptyPlaylist) {
    Map<String, AssetEntry> assets = new LinkedHashMap<>();
    assets.put(
        "a", new AssetEntry("a", "IMAGE", "a", "sha-a", "image/png", null, null, null, null));
    Map<String, PlaylistEntry> playlists = new LinkedHashMap<>();
    playlists.put(
        PLAYLIST,
        new PlaylistEntry(
            PLAYLIST,
            "Oficinas",
            emptyPlaylist ? List.of() : List.of(new ItemEntry("a", 10, "COVER", false))));
    List<ScheduleEntry> schedules =
        withSchedule
            ? List.of(
                new ScheduleEntry(
                    UUID.randomUUID().toString(),
                    PLAYLIST,
                    10,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    true,
                    true))
            : List.of();
    return new ManifestDocument(
        3,
        null,
        id,
        "Europe/Madrid",
        null,
        List.of(new FileEntry("sha-a", 10, "image/png", "a.png")),
        assets,
        playlists,
        schedules);
  }

  private void manifest(boolean withSchedule, boolean emptyPlaylist) {
    PlayerManifest manifest = new PlayerManifest();
    manifest.setPlayerId(id);
    manifest.setVersion(3);
    manifest.setManifest(document(withSchedule, emptyPlaylist));
    when(manifests.latest(id)).thenReturn(manifest);
  }

  private String hashOfCurrentPlan() throws Exception {
    AracastPlan plan =
        AracastPlanner.plan(document(true, false), LocalDateTime.now(), IdleLogo.CEFA);
    return Hashes.sha256Hex(MAPPER.writeValueAsString(plan));
  }

  @Test
  void publishesAPendingVersionMarksItAppliedAndAuditsIt() {
    manifest(true, false);

    service.deliver(id);

    verify(deliveries).markDownloading(id, 3);
    verify(publisher).publish(eq("10.0.0.5"), any(AracastPlan.class), any());
    verify(playerService).recordPublished(eq(id), eq(3L), anyString());
    ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
    verify(audit)
        .recordSystem(
            eq("ARACAST_PUBLISH"),
            eq("PLAYER"),
            eq(id.toString()),
            eq("Pantalla"),
            eq(AuditOutcome.SUCCESS),
            details.capture());
    assertThat(details.getValue()).containsEntry("version", 3L).containsEntry("events", 1);
  }

  @Test
  void keepsTheVersionQueuedWhenThePlayerDoesNotAnswer() {
    manifest(true, false);
    when(commands.isReachable(anyString(), anyInt())).thenReturn(false);

    service.deliver(id);

    verify(publisher, never()).publish(anyString(), any(), any());
    verify(playerService, never()).recordFailure(any(), anyLong(), anyBoolean(), any());
    verify(deliveries, never()).markDownloading(any(), anyLong());
    verify(audit, never()).recordSystem(any(), any(), any(), any(), any(), any());
  }

  @Test
  void reportsAPlaylistTheAracastCannotPlayAndAuditsTheFailure() {
    manifest(true, true);

    service.deliver(id);

    verify(playerService).recordFailure(eq(id), eq(3L), eq(true), contains("Oficinas"));
    verify(publisher, never()).publish(anyString(), any(), any());
    verify(audit)
        .recordSystem(eq("ARACAST_PUBLISH"), any(), any(), any(), eq(AuditOutcome.FAILED), any());
  }

  @Test
  void recordsAndAuditsAFailureWhenPublishingBreaks() {
    manifest(true, false);
    when(publisher.publish(anyString(), any(), any()))
        .thenThrow(new IllegalStateException("zip roto"));

    service.deliver(id);

    verify(playerService).recordFailure(eq(id), eq(3L), eq(true), contains("zip roto"));
    verify(playerService, never()).recordPublished(any(), anyLong(), any());
    ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
    verify(audit)
        .recordSystem(any(), any(), any(), any(), eq(AuditOutcome.FAILED), details.capture());
    assertThat(details.getValue().get("error").toString()).contains("zip roto");
  }

  @Test
  void theSameFailureIsAuditedOnlyOnce() {
    manifest(true, true);

    service.deliver(id);
    service.deliver(id);

    verify(audit, times(1))
        .recordSystem(any(), any(), any(), any(), eq(AuditOutcome.FAILED), any());
  }

  @Test
  void doesNothingWhenTheSamePlanIsAlreadyPublished() throws Exception {
    player.setAppliedVersion(3);
    manifest(true, false);
    aracast.setPublishedHash(hashOfCurrentPlan());

    service.deliver(id);

    verify(publisher, never()).publish(anyString(), any(), any());
    verify(commands, never()).isReachable(anyString(), anyInt());
  }

  @Test
  void doesNotTouchAPlayerThatCefaTvNeverPublishedToWhenThereIsNothingToPublish() {
    manifest(false, false);

    service.deliver(id);

    verify(playerService).recordSkipped(id, 3);
    verify(publisher, never()).publish(anyString(), any(), any());
    verify(commands, never()).isReachable(anyString(), anyInt());
    verify(audit, never()).recordSystem(any(), any(), any(), any(), any(), any());
  }

  @Test
  void clearsTheScheduleOfAPlayerItHadPublishedToWhenEverythingIsRemoved() {
    manifest(false, false);
    aracast.setPublishedHash("anterior");

    service.deliver(id);

    verify(publisher).publish(eq("10.0.0.5"), any(AracastPlan.class), any());
    verify(playerService, never()).recordSkipped(any(), anyLong());
  }

  @Test
  void aRollingRefreshRepublishesWithoutAuditingEveryWindowMove() {
    player.setAppliedVersion(3);
    PlayerManifest manifest = new PlayerManifest();
    manifest.setPlayerId(id);
    manifest.setVersion(3);
    ManifestDocument base = document(true, false);
    Map<String, AssetEntry> assets = new LinkedHashMap<>(base.assets());
    assets.put(
        "w", new AssetEntry("w", "WEB", "w", null, null, null, "https://a.example/", 60, null));
    Map<String, PlaylistEntry> playlists = new LinkedHashMap<>();
    playlists.put(
        PLAYLIST,
        new PlaylistEntry(
            PLAYLIST,
            "Mixta",
            List.of(
                new ItemEntry("a", 10, "COVER", false), new ItemEntry("w", 20, "COVER", false))));
    manifest.setManifest(
        new ManifestDocument(
            3, null, id, "Europe/Madrid", null, base.files(), assets, playlists, base.schedules()));
    when(manifests.latest(id)).thenReturn(manifest);
    aracast.setPublishedHash("ventana anterior");

    service.deliver(id);

    verify(publisher).publish(eq("10.0.0.5"), any(AracastPlan.class), any());
    verify(playerService).recordPublished(eq(id), eq(3L), anyString());
    verify(audit, never()).recordSystem(any(), any(), any(), any(), any(), any());
  }

  @Test
  void aRemoteSessionFreezesTheCalendarOfAPlayerItPublishedTo() {
    aracast.setPublishedHash("anterior");

    service.onRemoteSession(new AracastRemoteSessionEvent(player.getId(), true));

    verify(publisher, timeout(2000)).hold("10.0.0.5");
    verify(playerService, timeout(2000)).recordHeld(player.getId());
  }

  @Test
  void aRemoteSessionDoesNotFreezeAPlayerCefaTvNeverPublishedTo() throws Exception {
    aracast.setPublishedHash(null);

    service.onRemoteSession(new AracastRemoteSessionEvent(player.getId(), true));

    Thread.sleep(300);
    verify(publisher, never()).hold(anyString());
    verify(playerService, never()).recordHeld(any());
  }
}
