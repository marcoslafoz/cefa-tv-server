package es.cefa.tv.core.player.service;

import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.site.model.Site;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Determina la zona horaria con la que se evalúa la programación de un reproductor: la de su sede
 * si tiene sede y, si no, la que comunica el propio reproductor.
 */
@Component
@RequiredArgsConstructor
public class PlayerTimezoneResolver {

  private static final String FALLBACK = "UTC";

  private final SiteRepository sites;

  /**
   * Carga las zonas horarias de todas las sedes, para resolver varios reproductores sin una
   * consulta por cada uno.
   *
   * @return la zona horaria de cada sede por su identificador
   */
  public Map<UUID, String> siteTimezones() {
    return sites.findAll().stream()
        .collect(Collectors.toMap(Site::getId, Site::getTimezone, (first, second) -> first));
  }

  /**
   * Resuelve la zona horaria de un reproductor consultando su sede.
   *
   * @param player reproductor
   * @return identificador de la zona horaria
   */
  public String timezoneOf(Player player) {
    Function<UUID, String> lookup =
        siteId -> sites.findById(siteId).map(Site::getTimezone).orElse(null);
    return resolve(player, lookup);
  }

  /**
   * Resuelve la zona horaria de un reproductor con las zonas de sede ya cargadas.
   *
   * @param player reproductor
   * @param siteTimezones zonas de las sedes, de {@link #siteTimezones()}
   * @return identificador de la zona horaria
   */
  public String timezoneOf(Player player, Map<UUID, String> siteTimezones) {
    return resolve(player, siteTimezones::get);
  }

  /**
   * Convierte el identificador de zona en un {@link ZoneId}, con UTC si no es válido.
   *
   * @param timezone identificador de zona
   * @return la zona horaria
   */
  public static ZoneId zoneOf(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException e) {
      return ZoneId.of(FALLBACK);
    }
  }

  /** Aplica la regla de resolución con un origen de zonas de sede. */
  private static String resolve(Player player, Function<UUID, String> siteZone) {
    if (player.getSiteId() != null) {
      String zone = siteZone.apply(player.getSiteId());
      if (zone != null) {
        return zone;
      }
    }
    return player.getTimezone() == null || player.getTimezone().isBlank()
        ? FALLBACK
        : player.getTimezone();
  }
}
