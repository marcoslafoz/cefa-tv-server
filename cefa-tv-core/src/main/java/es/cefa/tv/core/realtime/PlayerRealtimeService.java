package es.cefa.tv.core.realtime;

import es.cefa.tv.core.delivery.service.DeliveryService;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerConfig;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerCommandService;
import es.cefa.tv.core.telemetry.service.TelemetryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Actualiza el estado persistente de un reproductor a partir de los mensajes WebSocket. */
@Service
@RequiredArgsConstructor
public class PlayerRealtimeService {

  private final PlayerRepository players;
  private final DeliveryService deliveryService;
  private final TelemetryService telemetryService;
  private final PlayerCommandService commandService;

  /**
   * Registra el contacto de un reproductor.
   *
   * @param playerId reproductor
   */
  @Transactional
  public void touch(UUID playerId) {
    players.findById(playerId).ifPresent(player -> player.markSeen(Instant.now()));
  }

  /**
   * Procesa el saludo inicial del reproductor.
   *
   * @param playerId reproductor
   * @param appliedVersion versión que tiene aplicada
   * @return {@code true} si el servidor tiene una versión más reciente que debe enviarse
   */
  @Transactional
  public boolean onHello(UUID playerId, long appliedVersion) {
    Optional<Player> found = players.findById(playerId);
    if (found.isEmpty()) {
      return false;
    }
    Player player = found.get();
    player.markSeen(Instant.now());
    player.setAppliedVersion(appliedVersion);
    if (appliedVersion > 0) {
      deliveryService.markApplied(playerId, appliedVersion);
    }
    return player.getDesiredVersion() > appliedVersion;
  }

  /**
   * Devuelve la configuración que debe tener un reproductor.
   *
   * @param playerId reproductor
   * @return la configuración, o vacío si el reproductor no existe
   */
  @Transactional(readOnly = true)
  public Optional<PlayerConfig> config(UUID playerId) {
    return players.findById(playerId).map(commandService::configOf);
  }

  /**
   * Guarda la telemetría enviada por el reproductor por el WebSocket.
   *
   * @param playerId reproductor
   * @param system datos del equipo, o {@code null} si no vienen
   * @param displays salidas de vídeo, o {@code null} si no vienen
   */
  @Transactional
  public void onTelemetry(
      UUID playerId, Map<String, Object> system, List<Map<String, Object>> displays) {
    players
        .findById(playerId)
        .ifPresent(
            player -> {
              player.markSeen(Instant.now());
              telemetryService.record(player, system, displays);
            });
  }

  /**
   * Actualiza lo que está reproduciendo el reproductor.
   *
   * @param playerId reproductor
   * @param state estado de reproducción
   * @param currentPlaylistId playlist en reproducción
   * @param currentAssetId medio en reproducción
   */
  @Transactional
  public void onState(UUID playerId, String state, UUID currentPlaylistId, UUID currentAssetId) {
    players
        .findById(playerId)
        .ifPresent(
            player -> {
              player.markSeen(Instant.now());
              player.setState(state);
              player.setCurrentPlaylistId(currentPlaylistId);
              player.setCurrentAssetId(currentAssetId);
            });
  }
}
