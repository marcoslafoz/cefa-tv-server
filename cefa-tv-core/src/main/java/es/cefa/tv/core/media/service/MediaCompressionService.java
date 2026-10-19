package es.cefa.tv.core.media.service;

import es.cefa.tv.core.media.model.MediaAsset;
import es.cefa.tv.core.media.model.MediaStatus;
import es.cefa.tv.core.media.model.StoredFile;
import es.cefa.tv.core.media.repository.MediaAssetRepository;
import es.cefa.tv.core.media.repository.StoredFileRepository;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.storage.ObjectStorageService;
import es.cefa.tv.core.shared.util.Hashes;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Comprime en segundo plano los vídeos subidos, de uno en uno. El medio queda en {@code PROCESSING}
 * hasta que termina; si la compresión falla o no reduce el tamaño se conserva el original.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCompressionService {

  private final MediaCompressor compressor;
  private final FfmpegService ffmpeg;
  private final ObjectStorageService storage;
  private final MediaAssetRepository assets;
  private final StoredFileRepository storedFiles;
  private final TransactionTemplate transactions;
  private final ApplicationEventPublisher events;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();

  /**
   * Encola la compresión de un vídeo cuando se confirma la subida.
   *
   * @param request vídeo a comprimir
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onRequested(MediaCompressionRequested request) {
    worker.submit(() -> run(request));
  }

  /** Tras un reinicio, los vídeos que se quedaron a medias se dejan como están, con su original. */
  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    List<MediaAsset> stuck = assets.findByStatus(MediaStatus.PROCESSING);
    for (MediaAsset asset : stuck) {
      markReady(asset.getId());
    }
    if (!stuck.isEmpty()) {
      log.warn("{} medios en proceso se marcan como listos con su original", stuck.size());
    }
  }

  void run(MediaCompressionRequested request) {
    MediaProgress.set(request.assetId(), 0);
    try {
      var compressed =
          compressor.compressVideo(
              request.original(),
              request.workDir(),
              percent -> MediaProgress.set(request.assetId(), percent));
      if (compressed.isPresent()) {
        replace(request.assetId(), compressed.get());
      } else {
        log.info("Vídeo {} sin cambios: se conserva el original", request.assetId());
        markReady(request.assetId());
      }
    } catch (RuntimeException | IOException e) {
      log.warn("No se pudo comprimir el vídeo {}: {}", request.assetId(), e.getMessage());
      markReady(request.assetId());
    } finally {
      MediaProgress.clear(request.assetId());
      deleteRecursively(request.workDir());
      events.publishEvent(new ContentChangedEvent("media-compressed"));
    }
  }

  private void replace(UUID assetId, Path compressed) throws IOException {
    String key = "media/" + assetId + "/compressed.mp4";
    long size = Files.size(compressed);
    String sha256 = sha256Of(compressed);
    var probe = ffmpeg.probe(compressed);
    storage.putFile(key, compressed, "video/mp4");
    String oldKey =
        transactions.execute(
            status -> {
              MediaAsset asset = assets.findById(assetId).orElseThrow();
              String previous = asset.getObjectKey();
              asset.setObjectKey(key);
              asset.setSha256(sha256);
              asset.setSizeBytes(size);
              asset.setMime("video/mp4");
              asset.setName(withMp4Extension(asset.getName()));
              probe.ifPresent(
                  p -> {
                    asset.setDurationMs(p.durationMs());
                    asset.setWidth(p.width());
                    asset.setHeight(p.height());
                  });
              asset.setStatus(MediaStatus.READY);
              storedFiles.deleteAll(storedFiles.findByAssetId(assetId));
              StoredFile file = new StoredFile();
              file.setId(UUID.randomUUID());
              file.setAssetId(assetId);
              file.setSha256(sha256);
              file.setObjectKey(key);
              file.setSizeBytes(size);
              file.setMime("video/mp4");
              file.setName(asset.getName());
              storedFiles.save(file);
              return previous;
            });
    if (oldKey != null && !oldKey.equals(key)) {
      try {
        storage.delete(oldKey);
      } catch (RuntimeException e) {
        log.warn("No se pudo borrar el original {}", oldKey, e);
      }
    }
    log.info("Vídeo {} comprimido a {} bytes", assetId, size);
  }

  private void markReady(UUID assetId) {
    transactions.executeWithoutResult(
        status ->
            assets
                .findById(assetId)
                .ifPresent(
                    asset -> {
                      if (asset.getStatus() == MediaStatus.PROCESSING) {
                        asset.setStatus(MediaStatus.READY);
                      }
                    }));
  }

  private static String sha256Of(Path file) throws IOException {
    MessageDigest digest = Hashes.sha256();
    try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
      in.transferTo(OutputStream.nullOutputStream());
    }
    return Hashes.hex(digest);
  }

  static String withMp4Extension(String name) {
    int dot = name.lastIndexOf('.');
    return (dot > 0 ? name.substring(0, dot) : name) + ".mp4";
  }

  private void deleteRecursively(Path dir) {
    try (var walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    } catch (IOException e) {
      log.warn("No se pudo limpiar {}", dir, e);
    }
  }

  /** Para el hilo al parar la aplicación. */
  @PreDestroy
  void shutdown() {
    worker.shutdownNow();
  }
}
