package es.cefa.tv.core.aracast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.cefa.tv.core.aracast.dto.CreateAracastPlayerRequest;
import es.cefa.tv.core.aracast.dto.PlayerStatus;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.exception.PlayerOfflineException;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.group.service.GroupSiteService;
import es.cefa.tv.core.manifest.service.ManifestService;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerKind;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerService;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AracastPlayerServiceTest {

  private static final PlayerStatus STATUS =
      new PlayerStatus(
          "10.0.0.5",
          "2.6.19",
          "AraCastPlayerDual",
          "00:01:80:89:55:0f",
          "10.0.0.5",
          "3 days",
          "1920x1080",
          "CEFA Oficinas",
          null,
          null,
          "basic_json",
          1000L,
          400L,
          "2026-10-19 10:00:00",
          "Europe/Madrid",
          0L);

  private AracastQueryService query;
  private AracastPlayerRepository aracasts;
  private PlayerRepository players;
  private ManifestService manifests;
  private AracastPlayerService service;

  @BeforeEach
  void setUp() {
    query = mock(AracastQueryService.class);
    aracasts = mock(AracastPlayerRepository.class);
    players = mock(PlayerRepository.class);
    manifests = mock(ManifestService.class);
    service =
        new AracastPlayerService(
            query,
            aracasts,
            players,
            mock(PlayerGroupRepository.class),
            mock(SiteRepository.class),
            mock(GroupSiteService.class),
            mock(AccessService.class),
            mock(PlayerService.class),
            manifests,
            mock(ApplicationEventPublisher.class),
            mock(es.cefa.tv.core.aracast.client.AracastSshClient.class),
            mock(es.cefa.tv.core.aracast.security.AracastCredentials.class),
            mock(es.cefa.tv.core.delivery.service.DeliveryService.class),
            mock(es.cefa.tv.core.aracast.remote.AracastBridge.class));
  }

  @Test
  void createsAnAracastPlayerWithItsReportedData() {
    when(query.getStatus("10.0.0.5")).thenReturn(STATUS);
    when(players.existsByName("Aracast 10.0.0.5")).thenReturn(true);

    service.create(new CreateAracastPlayerRequest("10.0.0.5", null, null, null, null, null, null));

    ArgumentCaptor<Player> player = ArgumentCaptor.forClass(Player.class);
    verify(players).saveAndFlush(player.capture());
    assertThat(player.getValue().getKind()).isEqualTo(PlayerKind.ARACAST);
    assertThat(player.getValue().getDeviceTokenHash()).isNull();
    assertThat(player.getValue().getName()).isEqualTo("Aracast 10.0.0.5 2");
    assertThat(player.getValue().getIp()).isEqualTo("10.0.0.5");
    assertThat(player.getValue().getPlayerVersion()).isEqualTo("2.6.19");
    assertThat(player.getValue().getTimezone()).isEqualTo("Europe/Madrid");
    assertThat(player.getValue().getResolution()).isEqualTo("1920x1080");
    assertThat(player.getValue().getLastSeenAt()).isNotNull();
    ArgumentCaptor<AracastPlayer> aracast = ArgumentCaptor.forClass(AracastPlayer.class);
    verify(aracasts).save(aracast.capture());
    assertThat(aracast.getValue().getHost()).isEqualTo("10.0.0.5");
    assertThat(aracast.getValue().getMac()).isEqualTo("00:01:80:89:55:0f");
    verify(manifests).rebuild(player.getValue().getId(), false);
  }

  @Test
  void rejectsAPlayerThatDoesNotAnswer() {
    when(query.getStatus("10.0.0.9")).thenThrow(new PlayerOfflineException("sin respuesta", null));

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateAracastPlayerRequest("10.0.0.9", null, null, null, null, null, null)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no responde");
    verify(players, never()).saveAndFlush(any());
  }

  @Test
  void rejectsAnEquipmentThatIsNotAnAracast() {
    when(query.getStatus("10.0.0.9")).thenThrow(new AracastException("respuesta no válida"));

    assertThatThrownBy(() -> service.probe("10.0.0.9"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no parece un player Aracast");
  }

  @Test
  void rejectsAPlayerAlreadyRegistered() {
    when(aracasts.findByHostIgnoreCase("10.0.0.5")).thenReturn(Optional.of(new AracastPlayer()));

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateAracastPlayerRequest("10.0.0.5", null, null, null, null, null, null)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("ya está dado de alta");
    verify(players, never()).saveAndFlush(any());
  }

  @Test
  void refreshesTheStateOfAnExistingPlayer() {
    UUID id = UUID.randomUUID();
    Player player = new Player();
    player.setId(id);
    AracastPlayer aracast = new AracastPlayer();
    aracast.setPlayerId(id);
    aracast.setHost("10.0.0.5");
    when(players.findById(id)).thenReturn(Optional.of(player));
    when(aracasts.findById(id)).thenReturn(Optional.of(aracast));
    when(manifests.latest(id)).thenThrow(ApiException.notFound("Manifest"));

    service.applyStatus(id, STATUS);

    assertThat(player.getPlayerVersion()).isEqualTo("2.6.19");
    assertThat(player.getLastSeenAt()).isNotNull();
    assertThat(player.getFirstConnectedAt()).isNotNull();
    assertThat(aracast.getModel()).isEqualTo("AraCastPlayerDual");
  }

  @Test
  void readsTheFtpPasswordFromTheFtpSectionOfCustomCfg() {
    String config =
        """
        [NETWORK]
        password = otra

        [FTP]
        port = 5481
        user = user
        password = clave-ftp

        [OTHER]
        password = tercera
        """;

    assertThat(AracastPlayerService.ftpPasswordFrom(config)).isEqualTo("clave-ftp");
    assertThat(AracastPlayerService.ftpPasswordFrom("[X]\na = 1")).isNull();
  }
}
