package es.cefa.tv.core.playerapi.dto;

import java.util.List;
import java.util.Map;

/**
 * Estado que el reproductor comunica periódicamente.
 *
 * @param appliedVersion versión aplicada
 * @param downloadingVersion versión que está descargando
 * @param state estado de reproducción
 * @param currentPlaylistId playlist en reproducción
 * @param currentAssetId medio en reproducción
 * @param diskFreeMb espacio libre en disco
 * @param uptimeSec tiempo en marcha
 * @param ip dirección IP local
 * @param error último error
 * @param system telemetría del equipo
 * @param displays salidas de vídeo detectadas
 */
public record HeartbeatRequest(
    Long appliedVersion,
    Long downloadingVersion,
    String state,
    String currentPlaylistId,
    String currentAssetId,
    Long diskFreeMb,
    Long uptimeSec,
    String ip,
    String error,
    Map<String, Object> system,
    List<Map<String, Object>> displays) {}
