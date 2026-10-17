package es.cefa.tv.core.schedule.dto;

import es.cefa.tv.core.schedule.model.ScheduleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Programación.
 *
 * @param id identificador
 * @param name nombre
 * @param playlistId playlist emitida
 * @param playlistName nombre de la playlist
 * @param playlistHidden si la playlist es implícita de un medio suelto
 * @param priority prioridad
 * @param startDate primer día de vigencia
 * @param endDate último día de vigencia
 * @param startTime inicio de la franja
 * @param endTime fin de la franja
 * @param daysOfWeek días de la semana
 * @param allDay si emite todo el día
 * @param enabled si está habilitada
 * @param targets destinos
 * @param thumbnailUrls miniaturas de los primeros ocho elementos de la playlist, en orden, con
 *     {@code null} donde no hay (webs y HTML)
 * @param itemCount número de elementos de la playlist
 * @param totalDurationSec duración total de la playlist en segundos
 * @param status estado actual de la programación, calculado en la zona horaria de sus destinos
 * @param nextRunAt inicio de la próxima emisión, o {@code null} si no volverá a emitirse
 * @param timezone zona horaria de los destinos cuando todos comparten la misma; {@code null} si hay
 *     varias
 * @param timezones zonas horarias distintas de los reproductores destino
 */
public record ScheduleResponse(
    UUID id,
    String name,
    UUID playlistId,
    String playlistName,
    boolean playlistHidden,
    int priority,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    List<Integer> daysOfWeek,
    boolean allDay,
    boolean enabled,
    ScheduleRequest.Targets targets,
    List<String> thumbnailUrls,
    int itemCount,
    int totalDurationSec,
    ScheduleStatus status,
    Instant nextRunAt,
    String timezone,
    List<String> timezones) {}
