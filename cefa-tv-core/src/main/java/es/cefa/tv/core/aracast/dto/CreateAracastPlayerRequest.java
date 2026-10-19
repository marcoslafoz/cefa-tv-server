package es.cefa.tv.core.aracast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Alta de un player Aracast.
 *
 * @param host IP o nombre de equipo del player
 * @param name nombre en CEFA TV; si falta se usa {@code Aracast <host>}
 * @param description descripción libre
 * @param groupId grupo de reproductores
 * @param siteId sede
 * @param sshPassword contraseña SSH de {@code root} propia de este player, opcional
 * @param ftpPassword contraseña del FTP propia de este player; si falta y hay SSH se lee del player
 */
public record CreateAracastPlayerRequest(
    @NotBlank @Size(max = 253) @Pattern(regexp = "[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?")
        String host,
    @Size(max = 150) String name,
    @Size(max = 500) String description,
    UUID groupId,
    UUID siteId,
    @Size(max = 200) String sshPassword,
    @Size(max = 200) String ftpPassword) {}
