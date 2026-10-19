package es.cefa.tv.core.aracast.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.cefa.tv.core.aracast.dto.DiscoveredPlayer;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.site.model.Site;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AracastSiteDiscoveryServiceTest {

  private final UUID siteId = UUID.randomUUID();
  private final UUID knownId = UUID.randomUUID();
  private final UUID missingId = UUID.randomUUID();
  private AracastDiscoveryService discovery;
  private SiteRepository sites;
  private AracastPlayerRepository aracasts;
  private PlayerRepository players;
  private Site site;
  private AracastSiteDiscoveryService service;

  @BeforeEach
  void setUp() {
    discovery = new AracastDiscoveryService(30333, 100, 2048);
    discovery = org.mockito.Mockito.spy(discovery);
    sites = mock(SiteRepository.class);
    aracasts = mock(AracastPlayerRepository.class);
    players = mock(PlayerRepository.class);
    site = new Site();
    site.setId(siteId);
    site.setIpRanges(List.of("10.0.0.1-10.0.0.4"));
    when(sites.findById(siteId)).thenReturn(Optional.of(site));
    service =
        new AracastSiteDiscoveryService(
            discovery, sites, aracasts, players, mock(AccessService.class));
  }

  private static AracastPlayer aracast(UUID id, String host, String mac) {
    AracastPlayer aracast = new AracastPlayer();
    aracast.setPlayerId(id);
    aracast.setHost(host);
    aracast.setMac(mac);
    return aracast;
  }

  @Test
  void marksWhatIsAlreadyRegisteredAndListsTheKnownPlayersThatDidNotAnswer() {
    org.mockito.Mockito.doReturn(
            List.of(
                new DiscoveredPlayer("10.0.0.2", "ARA2", "aa:aa", "AraCastPlayerDual", "2.6.19"),
                new DiscoveredPlayer("10.0.0.3", "ARA3", "bb:bb", "AraCastPlayer", "4.0.4")))
        .when(discovery)
        .scan(any());
    AracastPlayer known = aracast(knownId, "10.0.0.2", "aa:aa");
    when(aracasts.findByHostIgnoreCase("10.0.0.2")).thenReturn(Optional.of(known));
    when(aracasts.findByHostIgnoreCase("10.0.0.3")).thenReturn(Optional.empty());
    when(aracasts.findAll()).thenReturn(List.of(known, aracast(missingId, "10.0.0.4", "cc:cc")));
    Player p1 = new Player();
    p1.setId(knownId);
    Player p2 = new Player();
    p2.setId(missingId);
    when(players.findBySiteId(siteId)).thenReturn(List.of(p1, p2));
    when(aracasts.findById(knownId)).thenReturn(Optional.of(known));
    when(aracasts.findById(missingId))
        .thenReturn(Optional.of(aracast(missingId, "10.0.0.4", "cc:cc")));

    AracastSiteDiscoveryService.Result result = service.discover(siteId);

    assertThat(result.scanned()).isEqualTo(4);
    assertThat(result.found())
        .extracting(
            AracastSiteDiscoveryService.Hit::host, AracastSiteDiscoveryService.Hit::registered)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("10.0.0.2", true),
            org.assertj.core.groups.Tuple.tuple("10.0.0.3", false));
    assertThat(result.found().get(0).playerId()).isEqualTo(knownId);
    assertThat(result.offline()).containsExactly("10.0.0.4");
  }

  @Test
  void recognisesARegisteredPlayerByMacWhenItsAddressChanged() {
    org.mockito.Mockito.doReturn(
            List.of(new DiscoveredPlayer("10.0.0.3", "ARA", "aa:aa", "AraCastPlayer", "4.0.4")))
        .when(discovery)
        .scan(any());
    when(aracasts.findByHostIgnoreCase("10.0.0.3")).thenReturn(Optional.empty());
    when(aracasts.findAll()).thenReturn(List.of(aracast(knownId, "10.0.0.9", "AA:AA")));

    AracastSiteDiscoveryService.Result result = service.discover(siteId);

    assertThat(result.found().get(0).registered()).isTrue();
    assertThat(result.found().get(0).playerId()).isEqualTo(knownId);
  }

  @Test
  void aSiteWithoutRangesCannotBeScanned() {
    site.setIpRanges(null);

    assertThatThrownBy(() -> service.discover(siteId))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("rangos");
  }

  @Test
  void rangesBiggerThanTheLimitAreRejected() {
    site.setIpRanges(List.of("10.0.0.0/16", "10.1.0.0/16"));

    assertThatThrownBy(() -> service.discover(siteId)).isInstanceOf(ApiException.class);
  }
}
