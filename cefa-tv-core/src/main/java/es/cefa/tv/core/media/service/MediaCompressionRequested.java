package es.cefa.tv.core.media.service;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Un vídeo recién subido espera a que se comprima en segundo plano.
 *
 * @param assetId medio que se comprime
 * @param workDir directorio con el original, que el trabajo borra al terminar
 * @param original fichero original dentro de {@code workDir}
 */
public record MediaCompressionRequested(UUID assetId, Path workDir, Path original) {}
