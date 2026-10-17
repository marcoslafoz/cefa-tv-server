package es.cefa.tv.core.pairing.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Reproductor sin emparejar visible para el administrador. No incluye el código: quien empareja
 * debe leerlo en la pantalla del reproductor.
 *
 * @param id identificador de la petición
 * @param hostname nombre del equipo
 * @param ip dirección de origen
 * @param hardware datos de hardware
 * @param resolution resolución de la pantalla
 * @param playerVersion versión del reproductor
 * @param playerBuild commit corto con el que se construyó el reproductor
 * @param requestedAt instante de la petición
 * @param expiresAt caducidad del código
 */
public record PendingPairingResponse(
    UUID id,
    String hostname,
    String ip,
    Map<String, Object> hardware,
    String resolution,
    String playerVersion,
    String playerBuild,
    Instant requestedAt,
    Instant expiresAt) {}
