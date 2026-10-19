package es.cefa.tv.core.aracast.delivery;

import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.plan.AracastPlanner;
import es.cefa.tv.core.aracast.plan.PlannedContent;
import es.cefa.tv.core.aracast.template.MediaNaming;
import es.cefa.tv.core.media.model.StoredFile;
import es.cefa.tv.core.media.repository.StoredFileRepository;
import es.cefa.tv.core.shared.storage.ObjectStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prepara los ficheros de CEFA TV para un Aracast. El player identifica cada contenido por {@code
 * <nombre>_<sha1>.<ext>}; el SHA-1 se calcula una sola vez, al descargar el fichero por primera
 * vez, y se guarda para no volver a descargarlo solo para saber su nombre.
 */
@Component
@RequiredArgsConstructor
public class AracastContentService {

  private static final Map<String, String> EXTENSION_BY_MIME =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/gif", "gif",
          "image/bmp", "bmp",
          "image/webp", "webp",
          "video/mp4", "mp4",
          "video/webm", "webm",
          "video/quicktime", "mov",
          "text/html", "html");

  private final StoredFileRepository storedFiles;
  private final ObjectStorageService storage;
  private final PlatformTransactionManager transactions;

  /**
   * Nombre con el que un contenido se guarda en el player, descargándolo si todavía no se conoce su
   * SHA-1.
   *
   * @param content fichero del plan
   * @param workDir carpeta donde se deja la descarga, si hace falta
   * @return el nombre en el player
   */
  public String playerName(PlannedContent content, Path workDir) {
    if (isBuiltin(content)) {
      byte[] bytes = builtin(content);
      String name = MediaNaming.playerName(content.name(), sha1Of(bytes));
      writeFile(workDir.resolve(name), bytes);
      return name;
    }
    StoredFile file = find(content.sha256());
    String original = withExtension(content.name(), content.mime());
    if (file.getSha1() != null) {
      return MediaNaming.playerName(original, file.getSha1());
    }
    Path temporary = workDir.resolve("sha1-" + content.sha256());
    String sha1 = download(file, temporary);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status ->
                storedFiles
                    .findById(file.getId())
                    .ifPresent(
                        stored -> {
                          stored.setSha1(sha1);
                          storedFiles.save(stored);
                        }));
    String name = MediaNaming.playerName(original, sha1);
    try {
      Files.move(
          temporary, workDir.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new AracastException("No se pudo preparar " + content.name(), e);
    }
    return name;
  }

  /**
   * Deja el fichero en disco con el nombre del player, si todavía no está.
   *
   * @param content fichero del plan
   * @param playerName nombre en el player
   * @param workDir carpeta de trabajo
   * @return la ruta local
   */
  public Path fetch(PlannedContent content, String playerName, Path workDir) {
    Path target = workDir.resolve(playerName);
    if (!Files.exists(target) && isBuiltin(content)) {
      writeFile(target, builtin(content));
    } else if (!Files.exists(target)) {
      download(find(content.sha256()), target);
    }
    return target;
  }

  /**
   * Lee un fichero pequeño entero, como un HTML.
   *
   * @param sha256 resumen del fichero
   * @return su contenido
   */
  public byte[] read(String sha256) {
    try (InputStream in = storage.open(find(sha256).getObjectKey())) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new AracastException("No se pudo leer el fichero " + sha256, e);
    }
  }

  private static boolean isBuiltin(PlannedContent content) {
    return content.sha256().startsWith(AracastPlanner.BUILTIN);
  }

  /**
   * Lee un fichero que viene incluido en la aplicación, como la imagen de la pantalla en reposo.
   */
  private static byte[] builtin(PlannedContent content) {
    String id = content.sha256().substring(AracastPlanner.BUILTIN.length());
    try (InputStream in =
        AracastContentService.class.getResourceAsStream("/aracast/" + id + ".png")) {
      if (in == null) {
        throw new AracastException("Falta el fichero incluido " + id);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new AracastException("No se pudo leer el fichero incluido " + id, e);
    }
  }

  private static void writeFile(Path target, byte[] bytes) {
    try {
      Files.write(target, bytes);
    } catch (IOException e) {
      throw new AracastException("No se pudo preparar " + target.getFileName(), e);
    }
  }

  private static String sha1Of(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private String download(StoredFile file, Path target) {
    try (InputStream in = storage.open(file.getObjectKey())) {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      try (var out = Files.newOutputStream(target)) {
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
          out.write(buffer, 0, read);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new AracastException(
          "No se pudo descargar " + file.getName() + " del almacenamiento", e);
    }
  }

  private StoredFile find(String sha256) {
    List<StoredFile> found = storedFiles.findBySha256In(List.of(sha256));
    if (found.isEmpty()) {
      throw new AracastException("El fichero " + sha256 + " ya no está en el almacenamiento");
    }
    return found.get(0);
  }

  private static String withExtension(String name, String mime) {
    String base = name == null || name.isBlank() ? "contenido" : name;
    if (!MediaNaming.extension(base).isEmpty()) {
      return base;
    }
    String ext = mime == null ? null : EXTENSION_BY_MIME.get(mime.toLowerCase(Locale.ROOT));
    return ext == null ? base : base + "." + ext;
  }
}
