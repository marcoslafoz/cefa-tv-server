package es.cefa.tv.core.aracast.dto;

import jakarta.validation.constraints.Size;

/**
 * Cambio de las contraseñas de un player Aracast.
 *
 * @param sshPassword contraseña SSH de {@code root}; nula la deja como está y vacía la borra para
 *     usar las globales
 * @param ftpPassword contraseña del FTP; nula la deja como está, vacía la borra y, si se indica una
 *     SSH nueva sin FTP, se lee del propio player
 */
public record AracastCredentialsRequest(
    @Size(max = 200) String sshPassword, @Size(max = 200) String ftpPassword) {}
