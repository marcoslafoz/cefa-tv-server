package es.cefa.tv.core.aracast.service;

import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.exception.PlayerOfflineException;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.remote.AracastBridge;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.player.model.CommandType;
import es.cefa.tv.core.shared.exception.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Traduce las órdenes de un reproductor de CEFA TV a las que entiende un Aracast. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AracastCommandService {

  private final AracastPlayerRepository aracasts;
  private final AracastCommandClient commands;
  private final AracastBridge bridge;

  /**
   * Ejecuta una orden en un Aracast. Solo está disponible reiniciar el equipo.
   *
   * @param playerId reproductor
   * @param type orden
   */
  public void execute(UUID playerId, CommandType type) {
    if (type != CommandType.RESTART) {
      throw ApiException.badRequest(
          "COMMAND_NOT_SUPPORTED", "Esa orden no está disponible en un Aracast");
    }
    String host = hostOf(playerId);
    try {
      commands.send(host, "System.reboot");
    } catch (PlayerOfflineException e) {
      throw e;
    } catch (AracastException e) {
      log.debug("El player {} cerró la conexión al reiniciar: {}", host, e.getMessage());
    }
  }

  /**
   * Cierra lo que un Aracast tuviera abierto (pantalla remota, terminal) al darlo de baja.
   *
   * @param playerId reproductor
   */
  public void forget(UUID playerId) {
    bridge.forget(playerId);
  }

  /**
   * Dirección de un Aracast.
   *
   * @param playerId reproductor
   * @return IP o nombre con el que se le gestiona
   */
  public String hostOf(UUID playerId) {
    return aracasts
        .findById(playerId)
        .map(AracastPlayer::getHost)
        .orElseThrow(
            () -> ApiException.badRequest("NOT_ARACAST", "El reproductor no es un Aracast"));
  }
}
