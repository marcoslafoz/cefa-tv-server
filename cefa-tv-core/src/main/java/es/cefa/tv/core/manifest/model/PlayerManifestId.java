package es.cefa.tv.core.manifest.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Clave compuesta de un manifest.
 *
 * @param playerId reproductor
 * @param version versión
 */
public record PlayerManifestId(UUID playerId, long version) implements Serializable {}
