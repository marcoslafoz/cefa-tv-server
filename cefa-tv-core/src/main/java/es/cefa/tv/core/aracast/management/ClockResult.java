package es.cefa.tv.core.aracast.management;

/**
 * Hora de un Aracast.
 *
 * @param playerTime hora local del player
 * @param timezone zona horaria del player
 * @param offsetSeconds desfase respecto al servidor en segundos, positivo si adelanta
 * @param method cómo se sincronizó, o {@code null} si solo se leyó
 * @param detail información adicional de la sincronización
 */
public record ClockResult(
    String playerTime, String timezone, Long offsetSeconds, String method, String detail) {}
