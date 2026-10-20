package es.cefa.tv.core.aracast.remote;

import java.util.UUID;

/**
 * Evento publicado cuando se abre o se cierra el control remoto de un Aracast. Mientras dura, la
 * entrega de programaciones congela el calendario del player para que la página que se está
 * manejando no se sustituya por la siguiente de la playlist.
 *
 * @param playerId reproductor
 * @param active {@code true} al abrirse la sesión, {@code false} al terminar
 */
public record AracastRemoteSessionEvent(UUID playerId, boolean active) {}
