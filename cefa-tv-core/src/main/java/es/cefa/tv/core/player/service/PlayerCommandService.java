package es.cefa.tv.core.player.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.core.player.dto.PendingCommand;
import es.cefa.tv.core.player.model.CommandType;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerCommand;
import es.cefa.tv.core.player.model.PlayerConfig;
import es.cefa.tv.core.player.repository.PlayerCommandRepository;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrega órdenes a los reproductores por WebSocket o, si están desconectados, por heartbeat. */
@Service
@RequiredArgsConstructor
public class PlayerCommandService {

  private final PlayerCommandRepository commands;
  private final PlayerSessionRegistry sessions;
  private final IdleLogoResolver idleLogos;
  private final ObjectMapper objectMapper;

  /**
   * Envía una orden sin parámetros al reproductor si está conectado; si no, la deja en cola.
   *
   * @param playerId reproductor
   * @param type orden
   */
  @Transactional
  public void send(UUID playerId, CommandType type) {
    send(playerId, type, Map.of());
  }

  /**
   * Envía una orden con parámetros al reproductor si está conectado; si no, la deja en cola.
   *
   * @param playerId reproductor
   * @param type orden
   * @param payload parámetros de la orden
   */
  @Transactional
  public void send(UUID playerId, CommandType type, Map<String, Object> payload) {
    if (sessions.sendToPlayer(playerId, toJson(new PendingCommand(type, payload)))) {
      return;
    }
    PlayerCommand command = new PlayerCommand();
    command.setId(UUID.randomUUID());
    command.setPlayerId(playerId);
    command.setType(type);
    command.setPayload(payload.isEmpty() ? null : payload);
    command.setCreatedAt(Instant.now());
    commands.save(command);
  }

  /**
   * Calcula la configuración que debe tener un reproductor: la salida de vídeo elegida, el logo
   * efectivo de la pantalla sin programación y el volumen.
   *
   * @param player reproductor
   * @return la configuración
   */
  public PlayerConfig configOf(Player player) {
    return new PlayerConfig(
        player.getPreferredOutput(), idleLogos.effective(player).logo(), player.getVolume());
  }

  /**
   * Envía al reproductor su configuración si está conectado; si no, la recibirá en su siguiente
   * heartbeat o saludo.
   *
   * @param player reproductor
   */
  public void sendConfig(Player player) {
    sessions.sendToPlayer(player.getId(), configJson(configOf(player)));
  }

  /**
   * Construye el mensaje de configuración que se envía por WebSocket.
   *
   * @param config configuración del reproductor
   * @return el mensaje JSON
   */
  public String configJson(PlayerConfig config) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("type", "CONFIG");
    node.put("preferredOutput", config.preferredOutput());
    node.put("idleLogo", config.idleLogo().name());
    node.put("volume", config.volume());
    return node.toString();
  }

  /**
   * Recoge las órdenes pendientes de un reproductor y las marca como entregadas.
   *
   * @param playerId reproductor
   * @return las órdenes pendientes, en orden de creación
   */
  @Transactional
  public List<PendingCommand> drainPending(UUID playerId) {
    List<PlayerCommand> pending =
        commands.findByPlayerIdAndDeliveredAtIsNullOrderByCreatedAtAsc(playerId);
    Instant now = Instant.now();
    pending.forEach(command -> command.setDeliveredAt(now));
    return pending.stream()
        .map(
            command ->
                new PendingCommand(
                    command.getType(),
                    command.getPayload() == null ? Map.of() : command.getPayload()))
        .toList();
  }

  /**
   * Serializa una orden como mensaje WebSocket con sus parámetros al mismo nivel que el tipo.
   *
   * @param command orden
   * @return el mensaje JSON
   */
  public String toJson(PendingCommand command) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("type", command.type().name());
    command.payload().forEach((key, value) -> node.set(key, objectMapper.valueToTree(value)));
    return node.toString();
  }
}
