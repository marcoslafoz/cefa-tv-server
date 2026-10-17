package es.cefa.tv.core.media.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.shared.config.CoreProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
