package es.cefa.tv.core.media.controller;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.folder.dto.MoveRequest;
import es.cefa.tv.core.folder.dto.MoveResponse;
import es.cefa.tv.core.folder.service.FolderScope;
import es.cefa.tv.core.media.dto.MediaResponse;
import es.cefa.tv.core.media.dto.MediaUpdateRequest;
import es.cefa.tv.core.media.dto.WebMediaRequest;
import es.cefa.tv.core.media.model.MediaAsset;
import es.cefa.tv.core.media.model.MediaType;
import es.cefa.tv.core.media.service.MediaService;
import es.cefa.tv.core.shared.dto.PageResponse;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shared.storage.ObjectStorageService;
import es.cefa.tv.core.shared.storage.RangeResponses;
import jakarta.validation.Valid;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Endpoints de la biblioteca de medios. */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

  private final MediaService mediaService;
  private final ObjectStorageService storage;

  /**
   * Lista medios.
   *
   * @param type filtro de tipo
   * @param q texto contenido en el nombre, en toda la biblioteca o en la carpeta indicada
   * @param folderId {@code root}, un identificador de carpeta o nada para toda la biblioteca
   * @param recursive si se incluye lo que cuelga de la carpeta indicada
   * @param page página
   * @param size tamaño de página
   * @return la página de medios
   */
  @GetMapping
  public PageResponse<MediaResponse> list(
      @RequestParam(required = false) MediaType type,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String folderId,
      @RequestParam(defaultValue = "false") boolean recursive,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "40") int size) {
    return mediaService.list(
        type, q, FolderScope.parse(folderId, recursive), page, Math.min(size, 200));
  }

  /**
   * Sube uno o varios ficheros.
   *
   * @param files ficheros
   * @param folderId carpeta de destino, o nada para la raíz
   * @param principal usuario autenticado
   * @return los medios creados
   */
  @RequiresPermission(Permission.MEDIA_MANAGE)
  @PostMapping(consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.CREATED)
  public List<MediaResponse> upload(
      @RequestParam("files") List<MultipartFile> files,
      @RequestParam(value = "folderId", required = false) UUID folderId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return mediaService.upload(files, principal.username(), folderId);
  }

  /**
   * Crea un medio web.
   *
   * @param request datos de la web
   * @param principal usuario autenticado
   * @return el medio creado
   */
  @RequiresPermission(Permission.MEDIA_MANAGE)
  @PostMapping("/web")
  @ResponseStatus(HttpStatus.CREATED)
  public MediaResponse createWeb(
      @Valid @RequestBody WebMediaRequest request,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return mediaService.createWeb(request, principal.username());
  }

  /**
   * Mueve varios medios a una carpeta.
   *
   * @param request medios y carpeta de destino ({@code null} para la raíz)
   * @return el número de medios movidos
   */
  @RequiresPermission(Permission.MEDIA_MANAGE)
  @PostMapping("/move")
  public MoveResponse move(@Valid @RequestBody MoveRequest request) {
    return new MoveResponse(mediaService.move(request.ids(), request.folderId()));
  }

  /**
   * Obtiene un medio.
   *
   * @param id identificador
   * @return el medio
   */
  @GetMapping("/{id}")
  public MediaResponse get(@PathVariable UUID id) {
    return mediaService.get(id);
  }

  /**
   * Modifica un medio.
   *
   * @param id identificador
   * @param request campos a cambiar
   * @return el medio actualizado
   */
  @RequiresPermission(Permission.MEDIA_MANAGE)
  @PatchMapping("/{id}")
  public MediaResponse update(
      @PathVariable UUID id, @Valid @RequestBody MediaUpdateRequest request) {
    return mediaService.update(id, request);
  }

  /**
   * Borra un medio.
   *
   * @param id identificador
   */
  @RequiresPermission(Permission.MEDIA_MANAGE)
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    mediaService.delete(id);
  }

  /**
   * Descarga el contenido original con soporte de rangos.
   *
   * @param id identificador
   * @param range cabecera {@code Range}
   * @return el contenido
   */
  @GetMapping("/{id}/content")
  public ResponseEntity<StreamingResponseBody> content(
      @PathVariable UUID id,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
    MediaAsset asset = mediaService.find(id);
    if (asset.getObjectKey() == null) {
      throw ApiException.notFound("Contenido");
    }
    return RangeResponses.serve(
        storage,
        asset.getObjectKey(),
        asset.getSizeBytes(),
        asset.getMime(),
        asset.getSha256(),
        range,
        asset.getName());
  }

  /**
   * Devuelve la miniatura.
   *
   * @param id identificador
   * @return la imagen JPEG
   */
  @GetMapping("/{id}/thumbnail")
  public ResponseEntity<StreamingResponseBody> thumbnail(@PathVariable UUID id) {
    MediaAsset asset = mediaService.find(id);
    if (asset.getThumbnailKey() == null) {
      throw ApiException.notFound("Miniatura");
    }
    String key = asset.getThumbnailKey();
    StreamingResponseBody body =
        out -> {
          try (InputStream in = storage.open(key)) {
            in.transferTo(out);
          }
        };
    return ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
        .body(body);
  }
}
