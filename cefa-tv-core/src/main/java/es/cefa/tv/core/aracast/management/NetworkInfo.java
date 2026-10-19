package es.cefa.tv.core.aracast.management;

/**
 * Red de un Aracast: lo guardado y lo activo ahora, que pueden no coincidir.
 *
 * @param configured configuración guardada, que se aplica al reiniciar
 * @param effective valores de la interfaz tal y como están ahora
 */
public record NetworkInfo(NetworkConfig configured, NetworkConfig effective) {}
