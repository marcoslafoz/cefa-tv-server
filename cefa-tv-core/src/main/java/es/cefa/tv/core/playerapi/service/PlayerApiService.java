package es.cefa.tv.core.playerapi.service;

import es.cefa.tv.core.delivery.service.DeliveryService;
import es.cefa.tv.core.pairing.service.PairingService;
import es.cefa.tv.core.player.dto.PendingCommand;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerConfig;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerCommandService;
import es.cefa.tv.core.playerapi.dto.AckRequest;
import es.cefa.tv.core.playerapi.dto.HeartbeatRequest;
import es.cefa.tv.core.playerapi.dto.HeartbeatResponse;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.telemetry.service.TelemetryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lógica de la API que consumen los reproductores. */
@Service
@RequiredArgsConstructor
public class PlayerApiService {

  private final PlayerRepository players;
  private final DeliveryService deliveryService;
  private final PlayerCommandService commandService;
  private final TelemetryService telemetryService;
  private final PairingService pairingService;

  /**
   * Registra el estado enviado por el reproductor y devuelve la versión deseada y las órdenes
   * pendientes.
   *
   * @param playerId reproductor autenticado
   * @param request estado comunicado
   * @return la versión deseada y las órdenes
   */
  @Transactional
  public HeartbeatResponse heartbeat(UUID playerId, HeartbeatRequest request) {
    Player player = find(playerId);
    pairingService.markConsumed(playerId);
    player.markSeen(Instant.now());
    if (request.ip() != null) {
      player.setIp(request.ip());
    }
    player.setState(request.state());
    player.setCurrentPlaylistId(parseUuid(request.currentPlaylistId()));
    player.setCurrentAssetId(parseUuid(request.currentAssetId()));
    player.setLastError(request.error());
    telemetryService.record(player, request.system(), request.displays());
    if (request.appliedVersion() != null) {
      player.setAppliedVersion(request.appliedVersion());
      if (request.appliedVersion() > 0) {
        deliveryService.markApplied(playerId, request.appliedVersion());
      }
    }
    if (request.downloadingVersion() != null) {
      deliveryService.markDownloading(playerId, request.downloadingVersion());
    }
    List<HeartbeatResponse.Command> commands =
        commandService.drainPending(playerId).stream().map(this::toCommand).toList();
    return new HeartbeatResponse(
        player.getDesiredVersion(), commands, toConfig(commandService.configOf(player)));
  }

  /**
   * Registra el resultado de aplicar una versión.
   *
   * @param playerId reproductor autenticado
   * @param request resultado
   */
  @Transactional
  public void acknowledge(UUID playerId, AckRequest request) {
    Player player = find(playerId);
    player.markSeen(Instant.now());
    if (request.status() == AckRequest.AckStatus.APPLIED) {
      player.setAppliedVersion(request.version());
      player.setLastError(null);
      deliveryService.markApplied(playerId, request.version());
    } else {
      player.setLastError(request.error());
      deliveryService.markFailed(playerId, request.version(), request.error());
    }
  }

  /** Convierte una orden pendiente en su representación para el heartbeat. */
  /** Convierte la configuración del reproductor en la parte de configuración del heartbeat. */
  private HeartbeatResponse.Config toConfig(PlayerConfig config) {
    return new HeartbeatResponse.Config(config.preferredOutput(), config.idleLogo().name());
  }

  private HeartbeatResponse.Command toCommand(PendingCommand command) {
    Map<String, Object> payload = command.payload();
    return new HeartbeatResponse.Command(
        command.type().name(),
        (String) payload.get("output"),
        payload.get("width") instanceof Number width ? width.intValue() : null,
        payload.get("height") instanceof Number height ? height.intValue() : null);
  }

  /** Busca el reproductor autenticado o responde 401 si ya no existe. */
  private Player find(UUID playerId) {
    return players
        .findById(playerId)
        .orElseThrow(
            () -> ApiException.unauthorized("INVALID_DEVICE_TOKEN", "Reproductor desconocido"));
  }

  /** Convierte un texto en UUID devolviendo {@code null} si no es válido. */
  private UUID parseUuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
