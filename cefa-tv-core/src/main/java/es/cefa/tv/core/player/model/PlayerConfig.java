package es.cefa.tv.core.player.model;

/**
 * Configuración que el servidor impone a un reproductor y que se le entrega en el heartbeat y por
 * WebSocket.
 *
 * @param preferredOutput salida de vídeo elegida, o {@code null} para la selección automática
 * @param idleLogo logo efectivo de la pantalla sin programación
 */
public record PlayerConfig(String preferredOutput, IdleLogo idleLogo) {}
