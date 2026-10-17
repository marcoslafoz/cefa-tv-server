package es.cefa.tv.core.playlist.controller;

import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.playlist.dto.PlaylistRequest;
import es.cefa.tv.core.playlist.dto.PlaylistResponse;
import es.cefa.tv.core.playlist.service.PlaylistService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de playlists. */
@RestController
@RequestMapping("/api/v1/playlists")
@RequiredArgsConstructor
public class PlaylistController {

  private final PlaylistService playlistService;

  /**
   * Lista las playlists; las ocultas (creadas al arrastrar un medio suelto) solo con {@code
   * includeHidden}.
   *
   * @param includeHidden si se incluyen las playlists ocultas
   * @param q texto contenido en el nombre
   * @return las playlists visibles
   */
  @GetMapping
  public List<PlaylistResponse> list(
      @RequestParam(defaultValue = "false") boolean includeHidden,
      @RequestParam(required = false) String q) {
    return playlistService.list(includeHidden, q);
  }

  /**
   * Crea una playlist.
   *
   * @param request datos y elementos
   * @return la playlist creada
   */
  @RequiresPermission(Permission.PLAYLIST_MANAGE)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlaylistResponse create(@Valid @RequestBody PlaylistRequest request) {
    return playlistService.create(request);
  }

  /**
   * Obtiene una playlist.
   *
   * @param id identificador
   * @return la playlist
   */
  @GetMapping("/{id}")
  public PlaylistResponse get(@PathVariable UUID id) {
    return playlistService.get(id);
  }

  /**
   * Reemplaza una playlist.
   *
   * @param id identificador
   * @param request nuevos datos y elementos
   * @return la playlist actualizada
   */
  @RequiresPermission(Permission.PLAYLIST_MANAGE)
  @PutMapping("/{id}")
  public PlaylistResponse replace(
      @PathVariable UUID id, @Valid @RequestBody PlaylistRequest request) {
    return playlistService.replace(id, request);
  }

  /**
   * Borra una playlist.
   *
   * @param id identificador
   */
  @RequiresPermission(Permission.PLAYLIST_MANAGE)
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    playlistService.delete(id);
  }
}
