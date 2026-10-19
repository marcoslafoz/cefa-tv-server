package es.cefa.tv.core.aracast.dto;

/**
 * Estado resumido de un player, obtenido de {@code Player.getVersion}.
 *
 * @param host IP o nombre con el que se ha consultado
 * @param version versión del software AraCastPlayer
 * @param model modelo del player según el propio software
 * @param mac dirección MAC de la interfaz principal
 * @param ip IP que el player cree tener en su interfaz principal
 * @param uptime tiempo encendido en el formato textual del player
 * @param resolution resolución actual de la pantalla
 * @param currentTemplate plantilla en emisión
 * @param nextTemplate siguiente plantilla programada
 * @param nextTemplateTime instante del siguiente cambio de plantilla
 * @param defaultTemplate plantilla que se emite cuando no hay evento activo
 * @param totalBytes capacidad del disco
 * @param freeBytes espacio libre del disco
 * @param playerTime hora local del player en el momento de la consulta
 * @param timezone zona horaria configurada en el player
 * @param clockOffsetSeconds adelanto (positivo) o retraso (negativo) del reloj del player respecto
 *     al servidor de CEFA TV
 */
public record PlayerStatus(
    String host,
    String version,
    String model,
    String mac,
    String ip,
    String uptime,
    String resolution,
    String currentTemplate,
    String nextTemplate,
    String nextTemplateTime,
    String defaultTemplate,
    Long totalBytes,
    Long freeBytes,
    String playerTime,
    String timezone,
    Long clockOffsetSeconds) {}
