package es.cefa.tv.core.aracast.remote;

/**
 * Una acción de ratón o teclado sobre la pantalla de un Aracast.
 *
 * @param type {@code click}, {@code scroll}, {@code key} o {@code text}
 * @param x posición horizontal entre 0 y 1 (clic y rueda)
 * @param y posición vertical entre 0 y 1 (clic y rueda)
 * @param button botón del ratón: 1 izquierdo, 2 central, 3 derecho
 * @param count pulsaciones del clic o pasos de la rueda (negativo hacia arriba)
 * @param key nombre de tecla X11 con modificadores opcionales, por ejemplo {@code ctrl+a}
 * @param text texto a teclear tal cual
 */
public record RemoteInput(
    String type, Double x, Double y, Integer button, Integer count, String key, String text) {}
