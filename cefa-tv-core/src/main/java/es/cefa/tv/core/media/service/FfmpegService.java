package es.cefa.tv.core.media.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.shared.config.CoreProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Envoltorio de {@code ffprobe} y {@code ffmpeg}; si no están instalados degrada sin fallar. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegService {

  private static final int TIMEOUT_SECONDS = 120;
  private static final int THUMBNAIL_WIDTH = 480;

  private final CoreProperties properties;
  private final ObjectMapper objectMapper;

  /**
   * Información básica de un fichero multimedia.
   *
   * @param durationMs duración en milisegundos o {@code null}
   * @param width ancho en píxeles o {@code null}
   * @param height alto en píxeles o {@code null}
   */
  public record ProbeResult(Long durationMs, Integer width, Integer height) {}

  /**
   * Analiza un fichero con {@code ffprobe}.
   *
   * @param file fichero a analizar
   * @return el resultado, o vacío si ffprobe no está disponible o falla
   */
  public Optional<ProbeResult> probe(Path file) {
    try {
      String output =
          run(
              List.of(
                  properties.media().ffprobePath(),
                  "-v",
                  "error",
                  "-print_format",
                  "json",
                  "-show_format",
                  "-show_streams",
                  file.toString()));
      JsonNode root = objectMapper.readTree(output);
      Long durationMs = null;
      JsonNode duration = root.path("format").path("duration");
      if (!duration.isMissingNode() && !duration.asText().isBlank()) {
        durationMs = Math.round(Double.parseDouble(duration.asText()) * 1000);
      }
      Integer width = null;
      Integer height = null;
      for (JsonNode stream : root.path("streams")) {
        if ("video".equals(stream.path("codec_type").asText())) {
          width = stream.path("width").asInt();
          height = stream.path("height").asInt();
          break;
        }
      }
      return Optional.of(new ProbeResult(durationMs, width, height));
    } catch (IOException | RuntimeException e) {
      log.warn("ffprobe no disponible o falló para {}: {}", file.getFileName(), e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Genera una miniatura JPEG con {@code ffmpeg}.
   *
   * @param input fichero de entrada
   * @param output fichero JPEG de salida
   * @param seekSeconds segundo del que extraer el fotograma
   * @return {@code true} si la miniatura se generó
   */
  public boolean thumbnail(Path input, Path output, int seekSeconds) {
    try {
      run(
          List.of(
              properties.media().ffmpegPath(),
              "-y",
              "-v",
              "error",
              "-ss",
              String.valueOf(seekSeconds),
              "-i",
              input.toString(),
              "-frames:v",
              "1",
              "-vf",
              "scale=" + THUMBNAIL_WIDTH + ":-2",
              output.toString()));
      return Files.exists(output) && Files.size(output) > 0;
    } catch (IOException | RuntimeException e) {
      log.warn("ffmpeg no disponible o falló para {}: {}", input.getFileName(), e.getMessage());
      return false;
    }
  }

  /**
   * Reduce y recomprime una foto JPEG o PNG. {@code ffmpeg} aplica la orientación EXIF, así que una
   * foto de móvil sale derecha aunque se pierdan los metadatos.
   *
   * @param input foto original
   * @param output fichero de salida, con la misma extensión que la entrada
   * @param maxWidth ancho máximo
   * @param maxHeight alto máximo
   * @param quality calidad JPEG de {@code ffmpeg} (2 a 31), solo para JPEG
   * @param png si la salida es PNG, que se recomprime sin pérdida
   * @return {@code true} si se generó la salida
   */
  public boolean compressImage(
      Path input, Path output, int maxWidth, int maxHeight, int quality, boolean png) {
    List<String> command = new ArrayList<>();
    command.addAll(
        List.of(
            properties.media().ffmpegPath(),
            "-y",
            "-v",
            "error",
            "-nostdin",
            "-i",
            input.toString(),
            "-frames:v",
            "1",
            "-vf",
            scaleFilter(maxWidth, maxHeight, false)));
    if (png) {
      command.addAll(List.of("-compression_level", "9"));
    } else {
      command.addAll(List.of("-q:v", String.valueOf(quality), "-pix_fmt", "yuvj420p"));
    }
    command.add(output.toString());
    return runCompression(command, output, TIMEOUT_SECONDS);
  }

  /**
   * Reduce y recomprime un vídeo a H.264 con audio AAC en un MP4 listo para reproducir mientras se
   * descarga. Mantiene la orientación y el audio, si lo hay.
   *
   * @param input vídeo original
   * @param output fichero MP4 de salida
   * @param maxWidth ancho máximo
   * @param maxHeight alto máximo
   * @param crf factor de calidad constante de x264
   * @param preset velocidad de x264
   * @param audioBitrate tasa de bits del audio, por ejemplo {@code 128k}
   * @param timeoutMinutes tiempo máximo
   * @return {@code true} si se generó la salida
   */
  public boolean compressVideo(
      Path input,
      Path output,
      int maxWidth,
      int maxHeight,
      int crf,
      String preset,
      String audioBitrate,
      int timeoutMinutes) {
    List<String> command =
        List.of(
            properties.media().ffmpegPath(),
            "-y",
            "-v",
            "error",
            "-nostdin",
            "-i",
            input.toString(),
            "-map",
            "0:v:0",
            "-map",
            "0:a:0?",
            "-vf",
            scaleFilter(maxWidth, maxHeight, true) + ",format=yuv420p",
            "-c:v",
            "libx264",
            "-preset",
            preset,
            "-crf",
            String.valueOf(crf),
            "-profile:v",
            "high",
            "-c:a",
            "aac",
            "-b:a",
            audioBitrate,
            "-movflags",
            "+faststart",
            output.toString());
    return runCompression(command, output, timeoutMinutes * 60);
  }

  /** Filtro que reduce al máximo indicado manteniendo la proporción y sin ampliar nunca. */
  private static String scaleFilter(int maxWidth, int maxHeight, boolean evenSides) {
    return "scale='min("
        + maxWidth
        + ",iw)':'min("
        + maxHeight
        + ",ih)':force_original_aspect_ratio=decrease"
        + (evenSides ? ":force_divisible_by=2" : "");
  }

  /** Ejecuta una compresión y devuelve si dejó un fichero de salida con contenido. */
  private boolean runCompression(List<String> command, Path output, int timeoutSeconds) {
    Path log = output.resolveSibling(output.getFileName() + ".log");
    try {
      Process process =
          new ProcessBuilder(command)
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .start();
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        log.toFile().delete();
        Files.deleteIfExists(output);
        throw new IOException("Tiempo de espera agotado");
      }
      if (process.exitValue() != 0) {
        String detail = Files.exists(log) ? Files.readString(log).trim() : "";
        Files.deleteIfExists(output);
        throw new IOException(
            "Código de salida "
                + process.exitValue()
                + (detail.isEmpty()
                    ? ""
                    : ": " + detail.substring(Math.max(0, detail.length() - 300))));
      }
      return Files.exists(output) && Files.size(output) > 0;
    } catch (IOException | RuntimeException e) {
      log.toFile().delete();
      FfmpegService.log.warn("No se pudo comprimir {}: {}", output.getFileName(), e.getMessage());
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } finally {
      log.toFile().delete();
    }
  }

  /** Ejecuta un proceso con tiempo máximo y devuelve su salida estándar. */
  private String run(List<String> command) throws IOException {
    Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
    try {
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IOException("Tiempo de espera agotado");
      }
      if (process.exitValue() != 0) {
        throw new IOException("Código de salida " + process.exitValue());
      }
      return output;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new IOException("Interrumpido", e);
    }
  }
}
