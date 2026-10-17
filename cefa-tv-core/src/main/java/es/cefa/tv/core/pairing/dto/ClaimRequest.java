package es.cefa.tv.core.pairing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Petición de un usuario para emparejar el reproductor que muestra un código.
 *
 * @param code código mostrado en la pantalla
 * @param name nombre que se da al reproductor, por defecto el nombre del equipo
 * @param groupId grupo de reproductores al que se asigna
 */
public record ClaimRequest(
    @NotBlank @Size(max = 20) String code, @Size(max = 150) String name, UUID groupId) {}
