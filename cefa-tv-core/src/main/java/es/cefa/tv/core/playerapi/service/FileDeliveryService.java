package es.cefa.tv.core.playerapi.service;

import es.cefa.tv.core.media.model.StoredFile;
import es.cefa.tv.core.media.repository.StoredFileRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shared.storage.ObjectStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Entrega de ficheros a los reproductores por resumen SHA-256. */
@Service
@RequiredArgsConstructor
public class FileDeliveryService {

  private final StoredFileRepository storedFiles;
  private final ObjectStorageService storage;

  /**
   * Busca un fichero por su resumen.
   *
   * @param sha256 resumen
   * @return el fichero
   */
  @Transactional(readOnly = true)
  public StoredFile find(String sha256) {
    return storedFiles.findBySha256In(List.of(sha256)).stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "FILE_NOT_FOUND",
                    "Fichero no encontrado",
                    Map.of("sha256", sha256)));
  }

  /**
   * Prepara un zip con los ficheros pedidos, uno por resumen, sin comprimir los formatos que ya lo
   * están.
   *
   * @param requested resúmenes pedidos
   * @return el cuerpo que escribe el zip en streaming
   */
  @Transactional(readOnly = true)
  public StreamingResponseBody zip(List<String> requested) {
    Set<String> unique = new LinkedHashSet<>(requested);
    Map<String, StoredFile> found = new HashMap<>();
    storedFiles.findBySha256In(unique).forEach(file -> found.putIfAbsent(file.getSha256(), file));
    for (String sha : unique) {
      if (!found.containsKey(sha)) {
        throw new ApiException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "FILE_NOT_FOUND",
            "Fichero no encontrado",
            Map.of("sha256", sha));
      }
    }
    return (OutputStream out) -> {
      try (ZipOutputStream zip = new ZipOutputStream(out)) {
        for (String sha : unique) {
          StoredFile file = found.get(sha);
          zip.setLevel(
              isAlreadyCompressed(file.getMime())
                  ? Deflater.NO_COMPRESSION
                  : Deflater.DEFAULT_COMPRESSION);
          zip.putNextEntry(new ZipEntry(sha));
          try (InputStream in = storage.open(file.getObjectKey())) {
            in.transferTo(zip);
          }
          zip.closeEntry();
        }
        zip.finish();
      } catch (IOException e) {
        throw new IOException("Fallo al enviar el lote de ficheros", e);
      }
    };
  }

  /** Indica si el formato ya va comprimido y no compensa deflatarlo de nuevo. */
  private boolean isAlreadyCompressed(String mime) {
    return mime.startsWith("video/")
        || mime.startsWith("image/") && !mime.equals("image/svg+xml")
        || mime.equals("application/zip")
        || mime.equals("application/pdf");
  }
}
