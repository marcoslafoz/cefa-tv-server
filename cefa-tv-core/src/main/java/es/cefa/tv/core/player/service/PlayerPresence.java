package es.cefa.tv.core.player.service;

import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import es.cefa.tv.core.shared.config.CoreProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Determina si un reproductor está en línea. */
@Component
@RequiredArgsConstructor
public class PlayerPresence {

  private final PlayerSessionRegistry sessions;
  private final CoreProperties properties;

  /**
   * Un reproductor está en línea si tiene el WebSocket abierto o contactó dentro de la ventana
   * configurada.
   *
   * @param player reproductor
   * @return {@code true} si está en línea
   */
  public boolean isOnline(Player player) {
    if (sessions.isConnected(player.getId())) {
      return true;
    }
    Instant lastSeen = player.getLastSeenAt();
    return lastSeen != null
        && lastSeen.isAfter(
            Instant.now().minus(Duration.ofSeconds(properties.player().onlineWindowSec())));
  }
}
