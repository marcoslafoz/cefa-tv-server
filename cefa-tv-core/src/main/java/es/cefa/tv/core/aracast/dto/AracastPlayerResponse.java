package es.cefa.tv.core.aracast.dto;

import java.util.UUID;

/**
 * Datos de conexión de un Aracast; nunca incluye las contraseñas.
 *
 * @param playerId reproductor
 * @param host IP o nombre
 * @param mac dirección MAC
 * @param model modelo
 * @param sshPasswordConfigured si tiene una contraseña SSH propia
 * @param ftpPasswordConfigured si tiene una contraseña FTP propia
 */
public record AracastPlayerResponse(
    UUID playerId,
    String host,
    String mac,
    String model,
    boolean sshPasswordConfigured,
    boolean ftpPasswordConfigured) {}
