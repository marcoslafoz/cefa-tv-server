package es.cefa.tv.core.player.dto;

import es.cefa.tv.core.player.model.CommandType;
import java.util.Map;

/**
 * Orden pendiente de entregar a un reproductor.
 *
 * @param type tipo de orden
 * @param payload parámetros de la orden, vacío si no tiene
 */
public record PendingCommand(CommandType type, Map<String, Object> payload) {}
