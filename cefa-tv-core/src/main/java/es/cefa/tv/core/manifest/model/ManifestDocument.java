package es.cefa.tv.core.manifest.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Documento de programación que descarga un reproductor (ver {@code architecture.md}).
 *
 * @param version versión del manifest
 * @param generatedAt instante de generación
 * @param playerId reproductor destinatario
 * @param timezone zona horaria con la que se evalúa la programación
 * @param fallbackPlaylistId playlist de reposo
 * @param files ficheros que el reproductor debe tener en disco
 * @param assets medios usados, por identificador
 * @param playlists playlists usadas, por identificador
 * @param schedules programaciones aplicables
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ManifestDocument(
    long version,
    Instant generatedAt,
    UUID playerId,
    String timezone,
    UUID fallbackPlaylistId,
    List<FileEntry> files,
    Map<String, AssetEntry> assets,
    Map<String, PlaylistEntry> playlists,
    List<ScheduleEntry> schedules) {

  /**
   * Fichero necesario.
   *
   * @param sha256 resumen del contenido
   * @param sizeBytes tamaño en bytes
   * @param mime tipo MIME
   * @param name nombre original
   */
  public record FileEntry(String sha256, long sizeBytes, String mime, String name) {}

  /**
   * Medio del manifest.
   *
   * @param id identificador
   * @param type tipo
   * @param name nombre
   * @param sha256 resumen del contenido (imagen, vídeo, HTML)
   * @param mime tipo MIME
   * @param durationMs duración en milisegundos
   * @param url dirección (web)
   * @param refreshSec segundos entre recargas (web)
   * @param pages páginas (PDF)
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AssetEntry(
      String id,
      String type,
      String name,
      String sha256,
      String mime,
      Long durationMs,
      String url,
      Integer refreshSec,
      List<PageEntry> pages) {}

  /**
   * Página de un PDF.
   *
   * @param index posición empezando en 0
   * @param sha256 resumen de la imagen
   */
  public record PageEntry(int index, String sha256) {}

  /**
   * Playlist del manifest.
   *
   * @param id identificador
   * @param name nombre
   * @param items elementos en orden
   */
  public record PlaylistEntry(String id, String name, List<ItemEntry> items) {}

  /**
   * Elemento de una playlist.
   *
   * @param assetId medio
   * @param durationSec segundos de reproducción (por página en un PDF)
   * @param fit ajuste a pantalla
   * @param muted si se silencia
   */
  public record ItemEntry(String assetId, int durationSec, String fit, boolean muted) {}

  /**
   * Programación del manifest.
   *
   * @param id identificador
   * @param playlistId playlist emitida
   * @param priority prioridad
   * @param startDate primer día de vigencia
   * @param endDate último día de vigencia
   * @param startTime inicio de la franja
   * @param endTime fin de la franja
   * @param daysOfWeek días de la semana
   * @param allDay si emite todo el día
   * @param enabled si está habilitada
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ScheduleEntry(
      String id,
      String playlistId,
      int priority,
      String startDate,
      String endDate,
      String startTime,
      String endTime,
      List<Integer> daysOfWeek,
      boolean allDay,
      boolean enabled) {}
}
