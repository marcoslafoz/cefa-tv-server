package es.cefa.tv.core.aracast.management;

/**
 * Configuración de red y nombre de un Aracast.
 *
 * @param hostname nombre del equipo
 * @param type tipo de la interfaz, {@code static} o {@code dhcp}
 * @param ip dirección IP
 * @param netmask máscara de red
 * @param gateway puerta de enlace
 * @param dnsPrimary DNS primario
 * @param dnsSecondary DNS secundario
 */
public record NetworkConfig(
    String hostname,
    String type,
    String ip,
    String netmask,
    String gateway,
    String dnsPrimary,
    String dnsSecondary) {}
