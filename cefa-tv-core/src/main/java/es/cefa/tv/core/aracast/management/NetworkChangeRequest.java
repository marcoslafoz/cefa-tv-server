package es.cefa.tv.core.aracast.management;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de red y nombre de un Aracast; se envía siempre la configuración completa.
 *
 * @param hostname nombre del equipo
 * @param ip dirección IP
 * @param netmask máscara de red
 * @param gateway puerta de enlace
 * @param dnsPrimary DNS primario
 * @param dnsSecondary DNS secundario, opcional
 * @param reboot si se reinicia el player; es obligatorio al cambiar IP, máscara o puerta
 */
public record NetworkChangeRequest(
    @NotBlank @Size(max = 63) String hostname,
    @NotBlank String ip,
    @NotBlank String netmask,
    @NotBlank String gateway,
    @NotBlank String dnsPrimary,
    String dnsSecondary,
    boolean reboot) {}
