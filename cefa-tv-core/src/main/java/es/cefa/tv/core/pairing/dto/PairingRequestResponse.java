package es.cefa.tv.core.pairing.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de pedir un emparejamiento.
 *
 * @param pairingId identificador de la petición
 * @param code código que se muestra en pantalla
 * @param secret secreto con el que el reproductor consulta el estado; solo se entrega ahora
 * @param expiresAt caducidad del código
 * @param pollIntervalSec segundos entre consultas de estado
 */
public record PairingRequestResponse(
    UUID pairingId, String code, String secret, Instant expiresAt, int pollIntervalSec) {}
