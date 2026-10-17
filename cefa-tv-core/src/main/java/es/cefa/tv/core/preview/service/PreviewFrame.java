package es.cefa.tv.core.preview.service;

import java.time.Instant;

/**
 * Última captura reducida de la pantalla de un reproductor.
 *
 * @param jpeg bytes de la imagen JPEG
 * @param at instante en que llegó al servidor
 */
public record PreviewFrame(byte[] jpeg, Instant at) {}
