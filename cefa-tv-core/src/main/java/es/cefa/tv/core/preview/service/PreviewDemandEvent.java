package es.cefa.tv.core.preview.service;

import java.util.UUID;

/**
 * Cambio en quién mira la vista previa de un reproductor. Los reproductores de CEFA TV lo atienden
 * por su WebSocket; los de otros tipos, como Aracast, lo atiende quien escuche este evento.
 *
 * @param playerId reproductor
 * @param intervalSec segundos entre fotogramas, o {@code null} si ya nadie lo mira
 */
public record PreviewDemandEvent(UUID playerId, Integer intervalSec) {}
