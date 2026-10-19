package es.cefa.tv.core.media.service;

import es.cefa.tv.core.folder.dto.FolderRef;
import es.cefa.tv.core.media.dto.MediaResponse;
import es.cefa.tv.core.media.model.MediaAsset;
import es.cefa.tv.core.media.model.MediaStatus;
import java.util.List;

/** Conversión de medios a su representación de API. */
public final class MediaMapper {

  /** Evita la instanciación de la clase de utilidades. */
  private MediaMapper() {}

  /**
   * Convierte un medio en su DTO sin calcular la ruta de su carpeta.
   *
   * @param asset medio
   * @return el DTO con las rutas de miniatura y contenido
   */
  public static MediaResponse toResponse(MediaAsset asset) {
    return toResponse(asset, null);
  }

  /**
   * Convierte un medio en su DTO con la ruta de su carpeta.
   *
   * @param asset medio
   * @param folderPath ruta de la carpeta desde la raíz, vacía si está en la raíz
   * @return el DTO con las rutas de miniatura y contenido
   */
  public static MediaResponse toResponse(MediaAsset asset, List<FolderRef> folderPath) {
    String base = "/api/v1/media/" + asset.getId();
    return new MediaResponse(
        asset.getId(),
        asset.getName(),
        asset.getType(),
        asset.getMime(),
        asset.getSizeBytes(),
        asset.getSha256(),
        asset.getDurationMs(),
        asset.getWidth(),
        asset.getHeight(),
        asset.getPageCount(),
        asset.getUrl(),
        asset.getRefreshSec(),
        asset.getStatus(),
        asset.getStatus() == MediaStatus.PROCESSING ? MediaProgress.get(asset.getId()) : null,
        asset.getThumbnailKey() == null ? null : base + "/thumbnail",
        asset.getObjectKey() == null ? null : base + "/content",
        asset.getCreatedAt(),
        asset.getFolderId(),
        folderPath);
  }
}
