package es.cefa.tv.core.preview.controller;

import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.annotation.RequiresScope;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.preview.service.PreviewFrame;
import es.cefa.tv.core.preview.service.PreviewService;
import es.cefa.tv.core.shared.exception.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Último fotograma de vista previa guardado de un reproductor, para el primer pintado. */
@RestController
@RequestMapping("/api/v1/players/{id}/preview")
@RequiredArgsConstructor
public class PreviewController {

  private static final String PREVIEW_AT_HEADER = "X-Preview-At";

  private final PreviewService previews;

  /**
   * Devuelve el último fotograma guardado.
   *
   * @param id reproductor
   * @return el JPEG con la hora de captura en {@code X-Preview-At}
   */
  @RequiresPermission(Permission.PLAYER_VIEW)
  @RequiresScope(target = RequiresScope.Target.PLAYER, level = AccessLevel.VIEW)
  @GetMapping
  public ResponseEntity<byte[]> latest(@PathVariable UUID id) {
    PreviewFrame frame = previews.latest(id);
    if (frame == null) {
      throw ApiException.notFound("Vista previa");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.noStore())
        .header(PREVIEW_AT_HEADER, frame.at().toString())
        .body(frame.jpeg());
  }
}
