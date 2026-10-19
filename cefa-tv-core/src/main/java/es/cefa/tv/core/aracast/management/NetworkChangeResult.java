package es.cefa.tv.core.aracast.management;

/**
 * Resultado de aplicar un cambio de red.
 *
 * @param host dirección con la que CEFA TV llegará al player a partir de ahora
 * @param ipChanged si cambió la IP, la máscara o la puerta de enlace
 * @param rebooting si se ordenó un reinicio
 * @param message explicación para el usuario
 */
public record NetworkChangeResult(
    String host, boolean ipChanged, boolean rebooting, String message) {}
