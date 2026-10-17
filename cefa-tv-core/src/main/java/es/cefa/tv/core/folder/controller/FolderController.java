package es.cefa.tv.core.folder.controller;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.folder.dto.FolderRef;
import es.cefa.tv.core.folder.dto.FolderRequest;
import es.cefa.tv.core.folder.dto.FolderResponse;
import es.cefa.tv.core.folder.dto.FolderUpdateRequest;
import es.cefa.tv.core.folder.service.FolderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Carpetas de la biblioteca de medios. Cualquier sesión puede leerlas; crear, mover y borrar exige
 * el permiso {@code MEDIA_MANAGE}, que comprueba el servicio.
 */
@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {

  private final FolderService folderService;

  /**
   * Lista las carpetas con sus contadores.
   *
   * @return las carpetas en una lista plana con su {@code parentId}
   */
  @GetMapping
  public List<FolderResponse> list() {
    return folderService.list();
  }

  /**
   * Devuelve la ruta de una carpeta desde la raíz.
   *
   * @param id carpeta
   * @return las migas de pan
   */
  @GetMapping("/{id}/path")
  public List<FolderRef> path(@PathVariable UUID id) {
    return folderService.path(id);
  }

  /**
   * Crea una carpeta.
   *
   * @param request nombre, padre y tipo
   * @param principal usuario autenticado
   * @return la carpeta creada
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FolderResponse create(
      @Valid @RequestBody FolderRequest request,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return folderService.create(request, principal.username());
  }

  /**
   * Renombra o mueve una carpeta.
   *
   * @param id carpeta
   * @param request nuevo nombre y/o padre
   * @return la carpeta actualizada
   */
  @PatchMapping("/{id}")
  public FolderResponse update(
      @PathVariable UUID id, @Valid @RequestBody FolderUpdateRequest request) {
    return folderService.update(id, request);
  }

  /**
   * Borra una carpeta.
   *
   * @param id carpeta
   * @param moveContentsToParent si el contenido sube a la carpeta padre en lugar de impedir el
   *     borrado
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean moveContentsToParent) {
    folderService.delete(id, moveContentsToParent);
  }
}
