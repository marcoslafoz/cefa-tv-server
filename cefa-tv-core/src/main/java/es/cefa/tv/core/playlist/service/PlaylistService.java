package es.cefa.tv.core.playlist.service;

import es.cefa.tv.core.media.model.MediaAsset;
import es.cefa.tv.core.media.model.MediaType;
import es.cefa.tv.core.media.repository.MediaAssetRepository;
import es.cefa.tv.core.media.service.MediaMapper;
import es.cefa.tv.core.playlist.dto.PlaylistItemRequest;
import es.cefa.tv.core.playlist.dto.PlaylistRequest;
import es.cefa.tv.core.playlist.dto.PlaylistResponse;
import es.cefa.tv.core.playlist.model.Fit;
import es.cefa.tv.core.playlist.model.Playlist;
import es.cefa.tv.core.playlist.model.PlaylistItem;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import es.cefa.tv.core.schedule.repository.ScheduleRepository;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión de playlists. */
@Service
@RequiredArgsConstructor
public class PlaylistService {

  private static final int MAX_THUMBNAILS = 8;

  private final PlaylistRepository playlists;
  private final MediaAssetRepository assets;
  private final ScheduleRepository schedules;
  private final ApplicationEventPublisher events;

  /**
   * Lista las playlists.
   *
   * @param includeHidden si se incluyen las playlists implícitas ocultas
   * @param query texto contenido en el nombre, o {@code null}
   * @return las playlists
   */
  @Transactional(readOnly = true)
  public List<PlaylistResponse> list(boolean includeHidden, String query) {
    List<Playlist> found =
        includeHidden
            ? playlists.findAllByOrderByUpdatedAtDesc()
            : playlists.findByHiddenFalseOrderByUpdatedAtDesc();
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    return found.stream()
        .filter(playlist -> playlist.getName().toLowerCase(Locale.ROOT).contains(needle))
        .map(PlaylistService::toResponse)
        .toList();
  }

  /**
   * Obtiene una playlist.
   *
   * @param id identificador
   * @return la playlist
   */
  @Transactional(readOnly = true)
  public PlaylistResponse get(UUID id) {
    return toResponse(find(id));
  }

  /**
   * Crea una playlist.
   *
   * @param request datos y elementos
   * @return la playlist creada
   */
  @Transactional
  public PlaylistResponse create(PlaylistRequest request) {
    Playlist playlist = new Playlist();
    playlist.setId(UUID.randomUUID());
    playlist.setCreatedAt(Instant.now());
    return save(playlist, request, Boolean.TRUE.equals(request.hidden()));
  }

  /**
   * Crea una playlist oculta con un único medio, usada al arrastrar un medio suelto al timeline.
   *
   * @param assetId medio
   * @param durationSec segundos de reproducción
   * @return la playlist oculta
   */
  @Transactional
  public Playlist createHiddenForAsset(UUID assetId, int durationSec) {
    MediaAsset asset = assets.findById(assetId).orElseThrow(() -> ApiException.notFound("Medio"));
    Playlist playlist = new Playlist();
    playlist.setId(UUID.randomUUID());
    playlist.setCreatedAt(Instant.now());
    save(
        playlist,
        new PlaylistRequest(
            asset.getName(),
            null,
            List.of(new PlaylistItemRequest(assetId, durationSec, Fit.COVER, true)),
            true),
        true);
    return playlist;
  }

  /**
   * Reemplaza el contenido de una playlist.
   *
   * @param id identificador
   * @param request nuevos datos y elementos
   * @return la playlist actualizada
   */
  @Transactional
  public PlaylistResponse replace(UUID id, PlaylistRequest request) {
    Playlist playlist = find(id);
    return save(
        playlist, request, request.hidden() == null ? playlist.isHidden() : request.hidden());
  }

  /**
   * Borra una playlist si no está programada.
   *
   * @param id identificador
   */
  @Transactional
  public void delete(UUID id) {
    Playlist playlist = find(id);
    if (schedules.existsByPlaylistId(id)) {
      throw ApiException.conflict("PLAYLIST_IN_USE", "La playlist está en una programación");
    }
    playlists.delete(playlist);
    events.publishEvent(new ContentChangedEvent("playlist-delete"));
  }

  /**
   * Borra una playlist sin comprobar su uso; la usa la programación al eliminar su playlist oculta.
   *
   * @param playlist playlist a borrar
   */
  @Transactional
  public void deleteHidden(Playlist playlist) {
    playlists.delete(playlist);
  }

  /**
   * Busca la entidad de una playlist.
   *
   * @param id identificador
   * @return la playlist
   */
  @Transactional(readOnly = true)
  public Playlist find(UUID id) {
    return playlists.findById(id).orElseThrow(() -> ApiException.notFound("Playlist"));
  }

  /**
   * Convierte una playlist en su DTO.
   *
   * @param playlist playlist
   * @return el DTO con la duración total
   */
  public static PlaylistResponse toResponse(Playlist playlist) {
    List<PlaylistResponse.Item> items = new ArrayList<>();
    int total = 0;
    for (PlaylistItem item : playlist.getItems()) {
      items.add(
          new PlaylistResponse.Item(
              item.getId(),
              item.getPosition(),
              MediaMapper.toResponse(item.getAsset()),
              item.getDurationSec(),
              item.getFit(),
              item.isMuted()));
      total += durationOf(item);
    }
    return new PlaylistResponse(
        playlist.getId(),
        playlist.getName(),
        playlist.getDescription(),
        playlist.isHidden(),
        items,
        total,
        playlist.getUpdatedAt());
  }

  /**
   * Resume una playlist para pintar sus miniaturas: las de los primeros ocho elementos en orden,
   * con {@code null} donde no hay miniatura (webs y HTML), el número de elementos y la duración
   * total.
   *
   * @param playlist playlist
   * @return el resumen
   */
  public static Summary summarize(Playlist playlist) {
    List<String> thumbnails = new ArrayList<>();
    int total = 0;
    for (PlaylistItem item : playlist.getItems()) {
      if (thumbnails.size() < MAX_THUMBNAILS) {
        thumbnails.add(MediaMapper.toResponse(item.getAsset()).thumbnailUrl());
      }
      total += durationOf(item);
    }
    return new Summary(thumbnails, playlist.getItems().size(), total);
  }

  /**
   * Resumen de una playlist.
   *
   * @param thumbnailUrls miniaturas de los primeros elementos, con {@code null} si no tienen
   * @param itemCount número de elementos
   * @param totalDurationSec duración total en segundos
   */
  public record Summary(List<String> thumbnailUrls, int itemCount, int totalDurationSec) {}

  /** Duración en segundos de un elemento, multiplicada por las páginas si es un PDF. */
  private static int durationOf(PlaylistItem item) {
    MediaAsset asset = item.getAsset();
    if (asset.getType() == MediaType.PDF && asset.getPageCount() != null) {
      return item.getDurationSec() * asset.getPageCount();
    }
    return item.getDurationSec();
  }

  /** Aplica los datos de la petición a la playlist, la guarda y avisa del cambio de contenido. */
  private PlaylistResponse save(Playlist playlist, PlaylistRequest request, boolean hidden) {
    playlist.setName(request.name());
    playlist.setDescription(request.description());
    playlist.setHidden(hidden);
    playlist.setUpdatedAt(Instant.now());
    playlist.getItems().clear();
    List<PlaylistItemRequest> requested = request.items() == null ? List.of() : request.items();
    for (int position = 0; position < requested.size(); position++) {
      PlaylistItemRequest source = requested.get(position);
      MediaAsset asset =
          assets
              .findById(source.assetId())
              .orElseThrow(
                  () ->
                      ApiException.badRequest(
                          "ASSET_NOT_FOUND", "Medio inexistente: " + source.assetId()));
      PlaylistItem item = new PlaylistItem();
      item.setId(UUID.randomUUID());
      item.setPlaylist(playlist);
      item.setPosition(position);
      item.setAsset(asset);
      item.setDurationSec(source.durationSec());
      item.setFit(source.fit() == null ? Fit.COVER : source.fit());
      item.setMuted(source.muted() == null || source.muted());
      playlist.getItems().add(item);
    }
    Playlist saved = playlists.save(playlist);
    events.publishEvent(new ContentChangedEvent("playlist-save"));
    return toResponse(saved);
  }
}
