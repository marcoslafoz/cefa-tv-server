package es.cefa.tv.core.shared.storage;

import java.io.InputStream;
import java.io.OutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Construye respuestas de descarga de objetos con soporte de cabecera {@code Range}. */
public final class RangeResponses {

  /** Evita la instanciación de la clase de utilidades. */
  private RangeResponses() {}

  /**
   * Responde con el objeto completo o con el tramo solicitado.
   *
   * @param storage servicio de almacenamiento
   * @param key clave del objeto
   * @param size tamaño total en bytes
   * @param mime tipo MIME del contenido
   * @param etag valor del ETag sin comillas
   * @param rangeHeader valor de la cabecera {@code Range} o {@code null}
   * @param filename nombre sugerido o {@code null}
   * @return la respuesta 200, 206 o 416
   */
  public static ResponseEntity<StreamingResponseBody> serve(
      ObjectStorageService storage,
      String key,
      long size,
      String mime,
      String etag,
      String rangeHeader,
      String filename) {
    long start = 0;
    long end = size - 1;
    boolean partial = false;
    if (rangeHeader != null && rangeHeader.startsWith("bytes=") && size > 0) {
      long[] range = parse(rangeHeader.substring(6), size);
      if (range == null) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
            .header(HttpHeaders.CONTENT_RANGE, "bytes */" + size)
            .build();
      }
      start = range[0];
      end = range[1];
      partial = true;
    }
    long length = size == 0 ? 0 : end - start + 1;
    long offset = start;
    StreamingResponseBody body =
        (OutputStream out) -> {
          if (length == 0) {
            return;
          }
          try (InputStream in = storage.open(key, offset, length)) {
            in.transferTo(out);
          }
        };
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
            .contentType(MediaType.parseMediaType(mime))
            .contentLength(length)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .eTag("\"" + etag + "\"");
    if (partial) {
      builder.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + size);
    }
    if (filename != null) {
      builder.header(
          HttpHeaders.CONTENT_DISPOSITION,
          "inline; filename=\"" + filename.replace("\"", "") + "\"");
    }
    return builder.body(body);
  }

  /** Interpreta un rango de bytes y devuelve {@code null} si no es satisfacible. */
  private static long[] parse(String spec, long size) {
    try {
      String first = spec.split(",")[0].trim();
      int dash = first.indexOf('-');
      if (dash < 0) {
        return null;
      }
      String from = first.substring(0, dash);
      String to = first.substring(dash + 1);
      long start;
      long end;
      if (from.isEmpty()) {
        long suffix = Long.parseLong(to);
        start = Math.max(0, size - suffix);
        end = size - 1;
      } else {
        start = Long.parseLong(from);
        end = to.isEmpty() ? size - 1 : Math.min(Long.parseLong(to), size - 1);
      }
      if (start > end || start >= size) {
        return null;
      }
      return new long[] {start, end};
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
