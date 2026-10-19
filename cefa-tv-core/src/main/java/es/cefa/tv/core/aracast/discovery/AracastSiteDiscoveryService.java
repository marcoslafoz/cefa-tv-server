package es.cefa.tv.core.aracast.discovery;

import es.cefa.tv.core.aracast.dto.DiscoveredPlayer;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shared.util.Ipv4Range;
import es.cefa.tv.core.site.model.Site;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Busca Aracast en los rangos de direcciones de una sede y dice cuáles ya están dados de alta. */
@Service
@RequiredArgsConstructor
public class AracastSiteDiscoveryService {

  private final AracastDiscoveryService discovery;
  private final SiteRepository sites;
  private final AracastPlayerRepository aracasts;
  private final PlayerRepository players;
  private final AccessService access;

  /**
   * Barre los rangos de la sede.
   *
   * @param siteId sede
   * @return lo encontrado y los Aracast de la sede que no han respondido
   */
  public Result discover(UUID siteId) {
    access.requireSiteLevel(siteId, AccessLevel.OPERATE);
    Site site = sites.findById(siteId).orElseThrow(() -> ApiException.notFound("Sede"));
    List<String> ranges = site.getIpRanges() == null ? List.of() : site.getIpRanges();
    if (ranges.isEmpty()) {
      throw ApiException.unprocessable(
          "SITE_WITHOUT_RANGES", "La sede no tiene rangos de direcciones definidos");
    }
    List<String> addresses;
    try {
      addresses = discovery.expand(ranges);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_IP_RANGE", e.getMessage());
    }
    long started = System.currentTimeMillis();
    List<DiscoveredPlayer> responses = discovery.scan(addresses);
    long duration = System.currentTimeMillis() - started;
    List<Hit> found =
        responses.stream()
            .sorted(Comparator.comparing(DiscoveredPlayer::host))
            .map(this::toHit)
            .toList();
    List<String> answered = responses.stream().map(DiscoveredPlayer::host).toList();
    return new Result(siteId, addresses.size(), duration, found, offline(site, ranges, answered));
  }

  private Hit toHit(DiscoveredPlayer player) {
    AracastPlayer known =
        aracasts
            .findByHostIgnoreCase(player.host())
            .or(() -> aracasts.findAll().stream().filter(a -> sameMac(a, player)).findFirst())
            .orElse(null);
    return new Hit(
        player.host(),
        player.hostname(),
        player.mac(),
        player.model(),
        player.version(),
        known != null,
        known == null ? null : known.getPlayerId());
  }

  private static boolean sameMac(AracastPlayer aracast, DiscoveredPlayer player) {
    return aracast.getMac() != null
        && player.mac() != null
        && aracast.getMac().equalsIgnoreCase(player.mac());
  }

  private List<String> offline(Site site, List<String> ranges, List<String> answered) {
    List<Ipv4Range> parsed = ranges.stream().map(Ipv4Range::parse).toList();
    List<String> missing = new ArrayList<>();
    for (Player player : players.findBySiteId(site.getId())) {
      aracasts
          .findById(player.getId())
          .map(AracastPlayer::getHost)
          .filter(host -> parsed.stream().anyMatch(range -> range.contains(host)))
          .filter(host -> !answered.contains(host))
          .ifPresent(missing::add);
    }
    return missing;
  }

  /**
   * Resultado de un barrido.
   *
   * @param siteId sede barrida
   * @param scanned direcciones barridas
   * @param durationMs duración del barrido
   * @param found equipos que han respondido
   * @param offline Aracast de la sede dentro de sus rangos que no han respondido
   */
  public record Result(
      UUID siteId, int scanned, long durationMs, List<Hit> found, List<String> offline) {}

  /**
   * Un equipo encontrado.
   *
   * @param host dirección
   * @param hostname nombre configurado en el player
   * @param mac MAC
   * @param model modelo del software
   * @param version versión del software
   * @param registered si ya está dado de alta en CEFA TV
   * @param playerId reproductor, si está dado de alta
   */
  public record Hit(
      String host,
      String hostname,
      String mac,
      String model,
      String version,
      boolean registered,
      UUID playerId) {}
}
