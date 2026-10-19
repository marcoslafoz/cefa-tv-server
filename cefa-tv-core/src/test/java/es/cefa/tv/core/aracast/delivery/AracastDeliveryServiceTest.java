package es.cefa.tv.core.aracast.delivery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.plan.AracastPlan;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.aracast.service.AracastPlayerService;
import es.cefa.tv.core.delivery.service.DeliveryService;
import es.cefa.tv.core.manifest.model.ManifestDocument;
import es.cefa.tv.core.manifest.model.PlayerManifest;
import es.cefa.tv.core.manifest.service.ManifestService;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AracastDeliveryServiceTest {

  private static final String PLAYLIST = "11111111-1111-1111-1111-111111111111";

  private final UUID id = UUID.randomUUID();
  private Player player;
  private AracastPlayer aracast;
  private ManifestService manifests;
  private AracastPublisher publisher;
  private AracastPlayerService playerService;
  private DeliveryService deliveries;
  private AracastCommandClient commands;
  private AracastDeliveryService service;

  @BeforeEach
  void setUp() {
    player = new Player();
    player.setId(id);
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
    playerService = mock(AracastPlayerService.class);
    deliveries = mock(DeliveryService.class);
    commands = mock(AracastCommandClient.class);
    service =
        new AracastDeliveryService(
            players,
            aracasts,
            manifests,
            publisher,
            playerService,
            deliveries,
            commands,
            new ObjectMapper());
  }

  private void manifest(ManifestDocument.PlaylistEntry... playlists) {
    Map<String, ManifestDocument.PlaylistEntry> map = new java.util.LinkedHashMap<>();
    for (var p : playlists) {
      map.put(p.id(), p);
    }
    PlayerManifest manifest = new PlayerManifest();
    manifest.setPlayerId(id);
    manifest.setVersion(3);
    manifest.setManifest(
        new ManifestDocument(
            3, null, id, "Europe/Madrid", null, List.of(), Map.of(), map, List.of()));
    when(manifests.latest(id)).thenReturn(manifest);
  }

  @Test
  void publishesAPendingVersionAndMarksItApplied() {
    manifest();
    when(commands.isReachable(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);

    service.deliver(id);

    verify(deliveries).markDownloading(id, 3);
    verify(publisher).publish(eq("10.0.0.5"), any(AracastPlan.class), any());
    verify(playerService).recordPublished(eq(id), eq(3L), anyString());
  }

  @Test
  void keepsTheVersionQueuedWhenThePlayerDoesNotAnswer() {
    manifest();
    when(commands.isReachable(anyString(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(false);

    service.deliver(id);

    verify(publisher, never()).publish(anyString(), any(), any());
    verify(playerService, never())
        .recordFailure(any(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    verify(deliveries, never()).markDownloading(any(), anyLong());
  }

  @Test
  void reportsAPlaylistTheAracastCannotPlay() {
    manifest(new ManifestDocument.PlaylistEntry(PLAYLIST, "Vacía", List.of()));
    when(commands.isReachable(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);

    service.deliver(id);

    verify(playerService)
        .recordFailure(eq(id), eq(3L), eq(true), org.mockito.ArgumentMatchers.contains("Vacía"));
    verify(publisher, never()).publish(anyString(), any(), any());
  }

  @Test
  void recordsAFailureWhenPublishingBreaks() {
    manifest();
    when(commands.isReachable(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
    when(publisher.publish(anyString(), any(), any()))
        .thenThrow(new IllegalStateException("zip roto"));

    service.deliver(id);

    verify(playerService)
        .recordFailure(eq(id), eq(3L), eq(true), org.mockito.ArgumentMatchers.contains("zip roto"));
    verify(playerService, never()).recordPublished(any(), anyLong(), any());
  }

  @Test
  void doesNothingWhenTheSameVersionIsAlreadyPublished() {
    player.setAppliedVersion(3);
    manifest();
    aracast.setPublishedHash(
        es.cefa.tv.core.shared.util.Hashes.sha256Hex("{\"templates\":[],\"events\":[]}"));

    service.deliver(id);

    verify(publisher, never()).publish(anyString(), any(), any());
    verify(commands, never()).isReachable(anyString(), org.mockito.ArgumentMatchers.anyInt());
  }
}
