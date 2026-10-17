package es.cefa.tv.core.schedule.dto;

import es.cefa.tv.core.schedule.model.ScheduleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ocurrencia de una programación en un día concreto, para pintar el timeline.
 *
 * @param scheduleId programación origen
 * @param name nombre
 * @param playlistId playlist emitida
 * @param playlistName nombre de la playlist
 * @param date día de la ocurrencia
 * @param startTime hora de inicio en formato {@code HH:mm}
 * @param endTime hora de fin en formato {@code HH:mm}, {@code 24:00} si llega a medianoche
 * @param priority prioridad
 * @param allDay si es de todo el día
 * @param thumbnailUrls miniaturas de los primeros ocho elementos de la playlist, en orden, con
 *     {@code null} donde no hay (webs y HTML)
 * @param itemCount número de elementos de la playlist
 * @param totalDurationSec duración total de la playlist en segundos
 * @param playerId reproductor en el que se emite la ocurrencia
 * @param playerName nombre del reproductor
 * @param timezone zona horaria en la que se evalúa en ese reproductor
 * @param status estado de la ocurrencia: pendiente, en curso o ya pasada
 * @param nextRunAt inicio de la próxima emisión de la programación en ese reproductor, o {@code
 *     null} si no volverá a emitirse
 */
public record TimelineOccurrence(
    UUID scheduleId,
    String name,
    UUID playlistId,
    String playlistName,
    LocalDate date,
    String startTime,
    String endTime,
    int priority,
    boolean allDay,
    List<String> thumbnailUrls,
    int itemCount,
    int totalDurationSec,
    UUID playerId,
    String playerName,
    String timezone,
    ScheduleStatus status,
    Instant nextRunAt) {}
