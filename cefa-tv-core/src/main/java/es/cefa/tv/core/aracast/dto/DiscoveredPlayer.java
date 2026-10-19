package es.cefa.tv.core.aracast.dto;

/**
 * Respuesta de un player al datagrama de descubrimiento.
 *
 * @param host IP desde la que ha respondido
 * @param hostname nombre configurado en el player
 * @param mac MAC de la interfaz principal
 * @param model modelo del software (p. ej. {@code AraCastPlayerDual})
 * @param version versión del software (p. ej. {@code 2.6.19})
 */
public record DiscoveredPlayer(
    String host, String hostname, String mac, String model, String version) {}
