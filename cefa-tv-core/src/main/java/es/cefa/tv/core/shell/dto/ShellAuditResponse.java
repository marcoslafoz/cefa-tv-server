package es.cefa.tv.core.shell.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Sesión de terminal remota auditada.
 *
 * @param id identificador
 * @param playerId reproductor
 * @param playerName nombre del reproductor al abrir la sesión
 * @param username usuario que la abrió
 * @param openedAt apertura
 * @param closedAt cierre, nulo si sigue abierta
 * @param bytesIn bytes enviados por el usuario
 * @param bytesOut bytes recibidos del reproductor
 * @param closeReason motivo del cierre
 */
public record ShellAuditResponse(
    UUID id,
    UUID playerId,
    String playerName,
    String username,
    Instant openedAt,
    Instant closedAt,
    long bytesIn,
    long bytesOut,
    String closeReason) {}
