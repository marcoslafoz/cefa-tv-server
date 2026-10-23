package es.cefa.tv.core.playerapi.dto;

import java.util.List;

/**
 * Respuesta al heartbeat.
 *
 * @param desiredVersion versión que debería tener el reproductor
 * @param commands órdenes pendientes
 * @param config configuración que el servidor impone al reproductor
 */
public record HeartbeatResponse(long desiredVersion, List<Command> commands, Config config) {

  /**
   * Configuración del reproductor.
   *
   * @param preferredOutput salida de vídeo elegida, o {@code null} para la selección automática
   * @param idleLogo logo efectivo de la pantalla sin programación: {@code CEFA} o {@code ALGONTEC}
   * @param volume volumen del sonido de los vídeos, de 0 a 100
   */
  public record Config(String preferredOutput, String idleLogo, int volume) {}

  /**
   * Orden para el reproductor.
   *
   * @param type tipo de orden: {@code RELOAD}, {@code RESTART}, {@code UNENROLL}, {@code
   *     DISPLAY_REDETECT} o {@code SET_RESOLUTION}
   * @param output salida de vídeo afectada ({@code SET_RESOLUTION}), {@code null} en el resto
   * @param width ancho pedido ({@code SET_RESOLUTION}), {@code null} en el resto
   * @param height alto pedido ({@code SET_RESOLUTION}), {@code null} en el resto
   */
  public record Command(String type, String output, Integer width, Integer height) {}
}
