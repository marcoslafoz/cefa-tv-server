package es.cefa.tv.core.playlist.dto;

import es.cefa.tv.core.media.dto.MediaResponse;
import es.cefa.tv.core.playlist.model.Fit;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Playlist con sus elementos.
 *
 * @param id identificador
 * @param name nombre
 * @param description descripción
 * @param hidden si es una playlist implícita creada al arrastrar un medio suelto
 * @param items elementos en orden
 * @param totalDurationSec duración total en segundos, contando cada página de un PDF
 * @param updatedAt última modificación
 */
public record PlaylistResponse(
    UUID id,
    String name,
    String description,
    boolean hidden,
    List<Item> items,
    int totalDurationSec,
    Instant updatedAt) {

  /**
   * Elemento de la playlist.
   *
   * @param id identificador del elemento
   * @param position posición
   * @param asset medio asociado
   * @param durationSec duración en segundos
   * @param fit ajuste
   * @param muted si se silencia
   */
  public record Item(
      UUID id, int position, MediaResponse asset, int durationSec, Fit fit, boolean muted) {}
}
