package es.cefa.tv.core.telemetry.dto;

import java.time.Instant;

/**
 * Punto de la serie de telemetría de un reproductor.
 *
 * @param at instante de la muestra
 * @param cpuPercent uso de CPU en porcentaje
 * @param ramUsedMb memoria usada en MB
 * @param diskFreeMb espacio libre en disco en MB
 * @param tempC temperatura en grados Celsius
 */
public record MetricPoint(
    Instant at, Double cpuPercent, Long ramUsedMb, Long diskFreeMb, Double tempC) {}
