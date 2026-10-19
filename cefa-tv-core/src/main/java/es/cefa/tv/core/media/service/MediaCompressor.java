package es.cefa.tv.core.media.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decide si compensa sustituir una foto o un vídeo subido por una versión comprimida y reducida a
 * la resolución máxima configurada. Solo se queda con el resultado si pesa menos que el original.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCompressor {

  private final FfmpegService ffmpeg;
  private final MediaCompressionProperties settings;

  /**
   * Comprime una foto JPEG o PNG.
   *
   * @param input foto original
   * @param name nombre del fichero subido, que decide el formato
   * @param workDir directorio de trabajo
   * @return la foto comprimida, o vacío si no es de un formato que se comprima o no se reduce
   */
  public Optional<Path> compressImage(Path input, String name, Path workDir) {
    String lower = name.toLowerCase(Locale.ROOT);
    boolean png = lower.endsWith(".png");
    if (!settings.enabled() || !(png || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
      return Optional.empty();
    }
    Path output = workDir.resolve("compressed" + (png ? ".png" : ".jpg"));
    boolean done =
        ffmpeg.compressImage(
            input, output, settings.maxWidth(), settings.maxHeight(), settings.imageQuality(), png);
    return accept(input, output, done);
  }

  /**
   * Comprime un vídeo a H.264/AAC en MP4.
   *
   * @param input vídeo original
   * @param workDir directorio de trabajo
   * @param onProgress recibe el avance, de 0 a 100
   * @return el vídeo comprimido, o vacío si falla o no se reduce
   */
  public Optional<Path> compressVideo(Path input, Path workDir, IntConsumer onProgress) {
    if (!settings.enabled()) {
      return Optional.empty();
    }
    Path output = workDir.resolve("compressed.mp4");
    boolean done =
        ffmpeg.compressVideo(
            input,
            output,
            settings.maxWidth(),
            settings.maxHeight(),
            settings.videoCrf(),
            settings.videoPreset(),
            settings.audioBitrate(),
            settings.videoTimeoutMinutes(),
            onProgress);
    return accept(input, output, done);
  }

  /** Se queda con la salida solo si es más pequeña que la entrada por el margen mínimo. */
  private Optional<Path> accept(Path input, Path output, boolean done) {
    if (!done) {
      return Optional.empty();
    }
    try {
      long before = Files.size(input);
      long after = Files.size(output);
      long limit = before - before * settings.minSavingPercent() / 100;
      if (after > 0 && after <= limit) {
        return Optional.of(output);
      }
      Files.deleteIfExists(output);
    } catch (IOException e) {
      log.warn("No se pudo evaluar la compresión de {}: {}", input, e.getMessage());
    }
    return Optional.empty();
  }
}
