package es.cefa.tv.core.media.service;

import es.cefa.tv.core.folder.dto.FolderRef;
import es.cefa.tv.core.folder.service.FolderScope;
import es.cefa.tv.core.folder.service.FolderService;
import es.cefa.tv.core.media.dto.MediaResponse;
import es.cefa.tv.core.media.dto.MediaUpdateRequest;
import es.cefa.tv.core.media.dto.WebMediaRequest;
import es.cefa.tv.core.media.model.MediaAsset;
import es.cefa.tv.core.media.model.MediaStatus;
import es.cefa.tv.core.media.model.MediaType;
import es.cefa.tv.core.media.model.PdfPage;
import es.cefa.tv.core.media.model.StoredFile;
import es.cefa.tv.core.media.repository.MediaAssetRepository;
import es.cefa.tv.core.media.repository.StoredFileRepository;
import es.cefa.tv.core.playlist.repository.PlaylistItemRepository;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.shared.dto.PageResponse;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shared.storage.ObjectStorageService;
import es.cefa.tv.core.shared.util.Hashes;
import jakarta.persistence.criteria.Predicate;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Gestión de la biblioteca de medios. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

  private static final int THUMBNAIL_WIDTH = 480;

  private final MediaAssetRepository assets;
  private final StoredFileRepository storedFiles;
  private final PlaylistItemRepository playlistItems;
  private final ObjectStorageService storage;
  private final FfmpegService ffmpeg;
  private final PdfRasterizer pdfRasterizer;
  private final CoreProperties properties;
  private final ApplicationEventPublisher events;
  private final FolderService folders;
  private final MediaCompressor compressor;
  private final MediaCompressionProperties compression;

  /**
   * Lista medios con filtros opcionales.
   *
   * @param type tipo a filtrar o {@code null}
   * @param query texto contenido en el nombre o {@code null}
   * @param scope carpeta a listar, o {@code null} para toda la biblioteca
   * @param page página, empezando en 0
   * @param size tamaño de página
   * @return la página de medios
   */
  @Transactional(readOnly = true)
  public PageResponse<MediaResponse> list(
      MediaType type, String query, FolderScope scope, int page, int size) {
    Set<UUID> scoped = scopedFolders(scope);
    Specification<MediaAsset> spec =
        (root, cq, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (scope != null && scope.root() && !scope.recursive()) {
            predicates.add(cb.isNull(root.get("folderId")));
          } else if (scoped != null) {
            predicates.add(root.get("folderId").in(scoped));
          }
          if (type != null) {
            predicates.add(cb.equal(root.get("type"), type));
          }
          if (query != null && !query.isBlank()) {
            predicates.add(
                cb.like(cb.lower(root.get("name")), "%" + query.toLowerCase(Locale.ROOT) + "%"));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    var result =
        assets.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    Map<UUID, List<FolderRef>> paths = folders.paths();
    return PageResponse.of(
        result,
        asset ->
            MediaMapper.toResponse(
                asset, asset.getFolderId() == null ? List.of() : paths.get(asset.getFolderId())));
  }

  /**
   * Obtiene un medio.
   *
   * @param id identificador
   * @return el medio
   */
  @Transactional(readOnly = true)
  public MediaResponse get(UUID id) {
    return response(find(id));
  }

  /**
   * Busca la entidad de un medio.
   *
   * @param id identificador
   * @return la entidad
   */
  @Transactional(readOnly = true)
  public MediaAsset find(UUID id) {
    return assets.findById(id).orElseThrow(() -> ApiException.notFound("Medio"));
  }

  /**
   * Procesa y guarda los ficheros subidos.
   *
   * @param files ficheros multipart
   * @param username usuario que sube
   * @param folderId carpeta de destino, o {@code null} para la raíz
   * @return los medios creados
   */
  public List<MediaResponse> upload(List<MultipartFile> files, String username, UUID folderId) {
    requireMediaFolder(folderId);
    List<MediaResponse> created = new ArrayList<>();
    for (MultipartFile file : files) {
      created.add(uploadOne(file, username, folderId));
    }
    events.publishEvent(new ContentChangedEvent("media-upload"));
    return created;
  }

  /**
   * Crea un medio de tipo web.
   *
   * @param request datos de la web
   * @param username usuario que lo crea
   * @return el medio creado
   */
  @Transactional
  public MediaResponse createWeb(WebMediaRequest request, String username) {
    requireMediaFolder(request.folderId());
    MediaAsset asset = newAsset(request.name(), MediaType.WEB, username);
    asset.setFolderId(request.folderId());
    asset.setUrl(request.url());
    asset.setRefreshSec(request.refreshSec());
    return response(assets.save(asset));
  }

  /**
   * Modifica un medio.
   *
   * @param id identificador
   * @param request campos a cambiar
   * @return el medio actualizado
   */
  @Transactional
  public MediaResponse update(UUID id, MediaUpdateRequest request) {
    MediaAsset asset = find(id);
    if (request.name() != null && !request.name().isBlank()) {
      asset.setName(request.name());
    }
    if (request.durationMs() != null) {
      asset.setDurationMs(request.durationMs());
    }
    if (asset.getType() == MediaType.WEB) {
      if (request.url() != null) {
        asset.setUrl(request.url());
      }
      if (request.refreshSec() != null) {
        asset.setRefreshSec(request.refreshSec());
      }
    }
    applyFolder(asset, request);
    events.publishEvent(new ContentChangedEvent("media-update"));
    return response(asset);
  }

  /**
   * Mueve varios medios a una carpeta; las referencias desde playlists no cambian.
   *
   * @param ids medios a mover
   * @param folderId carpeta de destino, o {@code null} para la raíz
   * @return el número de medios movidos
   */
  @Transactional
  public int move(List<UUID> ids, UUID folderId) {
    requireMediaFolder(folderId);
    List<MediaAsset> found = assets.findAllById(ids.stream().distinct().toList());
    if (found.size() != ids.stream().distinct().count()) {
      throw ApiException.notFound("Medio");
    }
    found.forEach(asset -> asset.setFolderId(folderId));
    return found.size();
  }

  /**
   * Borra un medio y sus ficheros.
   *
   * @param id identificador
   */
  @Transactional
  public void delete(UUID id) {
    MediaAsset asset = find(id);
    if (playlistItems.existsByAssetId(id)) {
      throw ApiException.conflict("MEDIA_IN_USE", "El medio está en una playlist");
    }
    List<String> keys = new ArrayList<>();
    storedFiles.findByAssetId(id).forEach(file -> keys.add(file.getObjectKey()));
    if (asset.getObjectKey() != null) {
      keys.add(asset.getObjectKey());
    }
    if (asset.getThumbnailKey() != null) {
      keys.add(asset.getThumbnailKey());
    }
    assets.delete(asset);
    assets.flush();
    keys.stream().distinct().forEach(this::deleteQuietly);
    events.publishEvent(new ContentChangedEvent("media-delete"));
  }

  /** Procesa y guarda un único fichero subido, limpiando lo ya subido si falla. */
  private MediaResponse uploadOne(MultipartFile file, String username, UUID folderId) {
    String name = cleanName(file.getOriginalFilename());
    MediaType type = detectType(name, file.getContentType());
    UUID id = UUID.randomUUID();
    Path workDir = null;
    List<String> uploadedKeys = new ArrayList<>();
    boolean handedOff = false;
    try {
      workDir = createWorkDir();
      Path original = workDir.resolve("original");
      MessageDigest digest = Hashes.sha256();
      try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
        Files.copy(in, original);
      }
      long size = Files.size(original);
      String sha256 = Hashes.hex(digest);
      String mime = effectiveMime(name, file.getContentType(), type);
      if (type == MediaType.IMAGE) {
        var compressed = compressor.compressImage(original, name, workDir);
        if (compressed.isPresent()) {
          Files.move(compressed.get(), original, StandardCopyOption.REPLACE_EXISTING);
          log.info("Imagen {} comprimida de {} a {} bytes", name, size, Files.size(original));
          size = Files.size(original);
          sha256 = sha256Of(original);
        }
      }

      MediaAsset asset = newAsset(name, type, username);
      asset.setFolderId(folderId);
      asset.setId(id);
      asset.setMime(mime);
      asset.setSizeBytes(size);
      asset.setSha256(sha256);
      List<StoredFile> files = new ArrayList<>();

      switch (type) {
        case IMAGE -> processImage(asset, original, workDir);
        case VIDEO -> processVideo(asset, original, workDir);
        case PDF -> processPdf(asset, original, workDir, files, uploadedKeys);
        case HTML -> {}
        default ->
            throw ApiException.badRequest("UNSUPPORTED_MEDIA", "Tipo de fichero no soportado");
      }

      String objectKey = "media/" + id + "/original";
      storage.putFile(objectKey, original, mime);
      uploadedKeys.add(objectKey);
      asset.setObjectKey(objectKey);
      if (type != MediaType.PDF) {
        files.add(storedFile(id, sha256, objectKey, size, mime, name));
      }

      Path thumbnail = workDir.resolve("thumb.jpg");
      if (Files.exists(thumbnail)) {
        String thumbnailKey = "media/" + id + "/thumb.jpg";
        storage.putFile(thumbnailKey, thumbnail, "image/jpeg");
        uploadedKeys.add(thumbnailKey);
        asset.setThumbnailKey(thumbnailKey);
      }
      boolean compressLater = type == MediaType.VIDEO && compression.enabled();
      asset.setStatus(compressLater ? MediaStatus.PROCESSING : MediaStatus.READY);
      MediaResponse response = persist(asset, files);
      if (compressLater) {
        events.publishEvent(new MediaCompressionRequested(id, workDir, original));
        handedOff = true;
      }
      return response;
    } catch (IOException e) {
      uploadedKeys.forEach(this::deleteQuietly);
      throw ApiException.unprocessable(
          "MEDIA_PROCESSING_FAILED", "No se pudo procesar " + name + ": " + e.getMessage());
    } catch (RuntimeException e) {
      uploadedKeys.forEach(this::deleteQuietly);
      throw e;
    } finally {
      if (!handedOff) {
        deleteRecursively(workDir);
      }
    }
  }

  /** Guarda el medio y sus ficheros descargables. */
  private MediaResponse persist(MediaAsset asset, List<StoredFile> files) {
    assets.save(asset);
    storedFiles.saveAll(files);
    return response(asset);
  }

  /** Extrae las dimensiones de una imagen y genera su miniatura. */
  private void processImage(MediaAsset asset, Path original, Path workDir) throws IOException {
    BufferedImage image = ImageIO.read(original.toFile());
    if (image != null) {
      asset.setWidth(image.getWidth());
      asset.setHeight(image.getHeight());
      ImageToolkit.writeJpeg(
          ImageToolkit.scaleToWidth(image, THUMBNAIL_WIDTH), workDir.resolve("thumb.jpg"));
      return;
    }
    ffmpeg
        .probe(original)
        .ifPresent(
            probe -> {
              asset.setWidth(probe.width());
              asset.setHeight(probe.height());
            });
    ffmpeg.thumbnail(original, workDir.resolve("thumb.jpg"), 0);
  }

  /** Extrae duración y dimensiones de un vídeo y genera su miniatura. */
  private void processVideo(MediaAsset asset, Path original, Path workDir) {
    ffmpeg
        .probe(original)
        .ifPresent(
            probe -> {
              asset.setDurationMs(probe.durationMs());
              asset.setWidth(probe.width());
              asset.setHeight(probe.height());
            });
    Path thumbnail = workDir.resolve("thumb.jpg");
    if (!ffmpeg.thumbnail(original, thumbnail, 1)) {
      ffmpeg.thumbnail(original, thumbnail, 0);
    }
  }

  /** Rasteriza las páginas de un PDF, las sube y genera la miniatura. */
  private void processPdf(
      MediaAsset asset,
      Path original,
      Path workDir,
      List<StoredFile> files,
      List<String> uploadedKeys)
      throws IOException {
    Path pagesDir = Files.createDirectory(workDir.resolve("pages"));
    List<Path> pageFiles = pdfRasterizer.rasterize(original, pagesDir).pageFiles();
    List<PdfPage> pages = new ArrayList<>();
    for (int index = 0; index < pageFiles.size(); index++) {
      Path pageFile = pageFiles.get(index);
      String key = "media/" + asset.getId() + "/pages/" + index + ".jpg";
      String sha = sha256Of(pageFile);
      long pageSize = Files.size(pageFile);
      storage.putFile(key, pageFile, "image/jpeg");
      uploadedKeys.add(key);
      pages.add(new PdfPage(index, key, sha, pageSize));
      files.add(
          storedFile(
              asset.getId(),
              sha,
              key,
              pageSize,
              "image/jpeg",
              asset.getName() + "-p" + (index + 1) + ".jpg"));
      if (index == 0) {
        BufferedImage first = ImageIO.read(pageFile.toFile());
        asset.setWidth(first.getWidth());
        asset.setHeight(first.getHeight());
        ImageToolkit.writeJpeg(
            ImageToolkit.scaleToWidth(first, THUMBNAIL_WIDTH), workDir.resolve("thumb.jpg"));
      }
    }
    asset.setPages(pages);
    asset.setPageCount(pages.size());
  }

  /** Crea el registro de un fichero descargable por los reproductores. */
  private StoredFile storedFile(
      UUID assetId, String sha256, String key, long size, String mime, String name) {
    StoredFile file = new StoredFile();
    file.setId(UUID.randomUUID());
    file.setAssetId(assetId);
    file.setSha256(sha256);
    file.setObjectKey(key);
    file.setSizeBytes(size);
    file.setMime(mime);
    file.setName(name);
    return file;
  }

  /** Convierte un medio en su DTO con la ruta de su carpeta. */
  private MediaResponse response(MediaAsset asset) {
    return MediaMapper.toResponse(asset, folders.pathOf(asset.getFolderId()));
  }

  /** Exige que la carpeta, si se indica, exista y sea de la biblioteca de medios. */
  private void requireMediaFolder(UUID folderId) {
    if (folderId != null) {
      folders.require(folderId);
    }
  }

  /** Mueve el medio a la carpeta o a la raíz según la petición. */
  private void applyFolder(MediaAsset asset, MediaUpdateRequest request) {
    if (Boolean.TRUE.equals(request.moveToRoot())) {
      asset.setFolderId(null);
    } else if (request.folderId() != null) {
      requireMediaFolder(request.folderId());
      asset.setFolderId(request.folderId());
    }
  }

  /** Obtiene las carpetas incluidas en el ámbito de un listado, o {@code null} si no filtra. */
  private Set<UUID> scopedFolders(FolderScope scope) {
    if (scope == null || scope.root()) {
      return null;
    }
    folders.require(scope.folderId());
    return scope.recursive() ? folders.subtree(scope.folderId()) : Set.of(scope.folderId());
  }

  /** Crea un medio nuevo, listo para usarse, con los datos comunes. */
  private MediaAsset newAsset(String name, MediaType type, String username) {
    MediaAsset asset = new MediaAsset();
    asset.setId(UUID.randomUUID());
    asset.setName(name);
    asset.setType(type);
    asset.setStatus(MediaStatus.READY);
    asset.setCreatedAt(Instant.now());
    asset.setCreatedBy(username);
    return asset;
  }

  /** Calcula el SHA-256 de un fichero local. */
  private String sha256Of(Path file) throws IOException {
    MessageDigest digest = Hashes.sha256();
    try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
      in.transferTo(java.io.OutputStream.nullOutputStream());
    }
    return Hashes.hex(digest);
  }

  /** Deduce el tipo de medio por extensión y tipo MIME declarado. */
  private MediaType detectType(String name, String contentType) {
    String lower = name.toLowerCase(Locale.ROOT);
    String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".pdf") || mime.equals("application/pdf")) {
      return MediaType.PDF;
    }
    if (lower.endsWith(".html") || lower.endsWith(".htm") || mime.equals("text/html")) {
      return MediaType.HTML;
    }
    if (mime.startsWith("video/") || lower.matches(".*\\.(mp4|webm|mov|mkv|avi|m4v)$")) {
      return MediaType.VIDEO;
    }
    if (mime.startsWith("image/") || lower.matches(".*\\.(png|jpe?g|gif|webp|bmp|avif|svg)$")) {
      return MediaType.IMAGE;
    }
    throw ApiException.badRequest("UNSUPPORTED_MEDIA", "Tipo de fichero no soportado: " + name);
  }

  /** Determina el tipo MIME que se guarda, corrigiendo los declarados incorrectamente. */
  private String effectiveMime(String name, String declared, MediaType type) {
    if (type == MediaType.PDF) {
      return "application/pdf";
    }
    if (type == MediaType.HTML) {
      return "text/html";
    }
    String prefix = type == MediaType.VIDEO ? "video/" : "image/";
    if (declared != null && declared.toLowerCase(Locale.ROOT).startsWith(prefix)) {
      return declared;
    }
    return MediaTypeFactory.getMediaType(name)
        .map(Object::toString)
        .orElse(type == MediaType.VIDEO ? "video/mp4" : "image/jpeg");
  }

  /** Quita cualquier ruta del nombre de fichero subido. */
  private String cleanName(String original) {
    String name = original == null || original.isBlank() ? "sin-nombre" : original;
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    return slash >= 0 ? name.substring(slash + 1) : name;
  }

  /** Crea el directorio temporal de trabajo de una subida. */
  private Path createWorkDir() throws IOException {
    String tempDir = properties.media().tempDir();
    return tempDir == null || tempDir.isBlank()
        ? Files.createTempDirectory("cefatv-upload-")
        : Files.createTempDirectory(Path.of(tempDir), "cefatv-upload-");
  }

  /** Borra un directorio temporal y su contenido. */
  private void deleteRecursively(Path dir) {
    if (dir == null) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    } catch (IOException e) {
      log.warn("No se pudo limpiar {}", dir, e);
    }
  }

  /** Borra un objeto del almacenamiento sin propagar errores. */
  private void deleteQuietly(String key) {
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      log.warn("No se pudo borrar el objeto {}", key, e);
    }
  }
}
