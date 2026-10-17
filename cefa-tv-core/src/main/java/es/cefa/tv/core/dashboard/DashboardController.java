package es.cefa.tv.core.dashboard;

import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.media.repository.MediaAssetRepository;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerPresence;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Totales para el panel principal. */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final PlayerRepository players;
  private final PlayerPresence presence;
  private final MediaAssetRepository media;
  private final PlaylistRepository playlists;
  private final AccessService access;

  /**
   * Calcula los totales del sistema.
   *
   * @return los totales
   */
  @GetMapping
  @Transactional(readOnly = true)
  public Summary summary() {
    List<Player> all = players.findAll().stream().filter(access::canViewPlayer).toList();
    long online = all.stream().filter(presence::isOnline).count();
    return new Summary(
        all.size(),
        online,
        all.size() - online,
        all.stream().filter(Player::isPending).count(),
        media.count(),
        playlists.findByHiddenFalseOrderByUpdatedAtDesc().size(),
        media.totalSizeBytes());
  }

  /**
   * Totales del sistema.
   *
   * @param players reproductores registrados
   * @param playersOnline reproductores en línea
   * @param playersOffline reproductores sin conexión
   * @param playersPending reproductores con programación pendiente de entregar
   * @param media medios en la biblioteca
   * @param playlists playlists visibles
   * @param storageBytes bytes ocupados por los medios
   */
  public record Summary(
      long players,
      long playersOnline,
      long playersOffline,
      long playersPending,
      long media,
      long playlists,
      long storageBytes) {}
}
