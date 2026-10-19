package es.cefa.tv.core.realtime;

import java.util.UUID;

/**
 * Atiende a un reproductor que no abre un WebSocket propio, como un Aracast: recibe los mismos
 * mensajes que el servidor enviaría al reproductor y responde por los visores del registro.
 */
public interface VirtualPlayerBridge {

  /**
   * Indica si el puente se encarga de este reproductor.
   *
   * @param playerId reproductor
   * @return {@code true} si lo atiende
   */
  boolean handles(UUID playerId);

  /**
   * Indica si el reproductor responde ahora.
   *
   * @param playerId reproductor
   * @return {@code true} si está en línea
   */
  boolean isOnline(UUID playerId);

  /**
   * Procesa un mensaje JSON dirigido al reproductor.
   *
   * @param playerId reproductor
   * @param json mensaje, por ejemplo {@code REMOTE_START} o {@code SHELL_OPEN}
   */
  void onMessage(UUID playerId, String json);
}
