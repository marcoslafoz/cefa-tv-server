package es.cefa.tv.core.shared.event;

/**
 * Evento publicado cuando cambia algo que puede afectar al manifest de algún reproductor.
 *
 * @param reason origen del cambio, solo para trazas
 */
public record ContentChangedEvent(String reason) {}
