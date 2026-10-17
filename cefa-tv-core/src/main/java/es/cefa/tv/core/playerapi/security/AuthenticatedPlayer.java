package es.cefa.tv.core.playerapi.security;

import java.util.UUID;

/**
 * Principal de una llamada autenticada con token de dispositivo.
 *
 * @param playerId reproductor
 */
public record AuthenticatedPlayer(UUID playerId) {}
