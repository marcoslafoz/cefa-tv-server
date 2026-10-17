package es.cefa.tv.core.manifest.service;

import es.cefa.tv.core.manifest.model.ManifestDocument;
import es.cefa.tv.core.media.model.MediaAsset;
import es.cefa.tv.core.media.model.MediaType;
import es.cefa.tv.core.media.model.PdfPage;
import es.cefa.tv.core.playlist.model.Playlist;
import es.cefa.tv.core.playlist.model.PlaylistItem;
import es.cefa.tv.core.schedule.model.Schedule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Construye de forma determinista el contenido de un manifest a partir del modelo de dominio. */
public final class ManifestAssembler {

  /** Evita la instanciación de la clase de utilidades. */
  private ManifestAssembler() {}

  /**
   * Ensambla el manifest con versión 0 y sin fecha, que sirve para calcular el resumen de
   * contenido.
   *
   * @param playerId reproductor destinatario
   * @param timezone zona horaria del reproductor
   * @param fallbackPlaylist playlist de reposo o {@code null}
   * @param schedules programaciones aplicables, ya filtradas por habilitadas
   * @param playlists playlists de las programaciones y la de reposo
   * @return el manifest sin versión ni fecha
   */
  public static ManifestDocument assemble(
      UUID playerId,
      String timezone,
      UUID fallbackPlaylist,
      Collection<Schedule> schedules,
      Collection<Playlist> playlists) {
    Map<String, ManifestDocument.PlaylistEntry> playlistEntries = new TreeMap<>();
    Map<String, ManifestDocument.AssetEntry> assetEntries = new TreeMap<>();
    Map<String, ManifestDocument.FileEntry> fileEntries = new LinkedHashMap<>();

    for (Playlist playlist : playlists) {
      List<ManifestDocument.ItemEntry> items = new ArrayList<>();
      for (PlaylistItem item : playlist.getItems()) {
        MediaAsset asset = item.getAsset();
        items.add(
            new ManifestDocument.ItemEntry(
                asset.getId().toString(),
                item.getDurationSec(),
                item.getFit().name(),
                item.isMuted()));
        assetEntries.computeIfAbsent(asset.getId().toString(), key -> toAssetEntry(asset));
        collectFiles(asset, fileEntries);
      }
      playlistEntries.put(
          playlist.getId().toString(),
          new ManifestDocument.PlaylistEntry(
              playlist.getId().toString(), playlist.getName(), items));
    }

    List<ManifestDocument.ScheduleEntry> scheduleEntries =
        schedules.stream()
            .sorted(Comparator.comparing(schedule -> schedule.getId().toString()))
            .map(ManifestAssembler::toScheduleEntry)
            .toList();
    List<ManifestDocument.FileEntry> files =
        fileEntries.values().stream()
            .sorted(Comparator.comparing(ManifestDocument.FileEntry::sha256))
            .toList();

    return new ManifestDocument(
        0,
        null,
        playerId,
        timezone,
        fallbackPlaylist,
        files,
        assetEntries,
        playlistEntries,
        scheduleEntries);
  }

  /** Convierte un medio en su entrada del manifest. */
  private static ManifestDocument.AssetEntry toAssetEntry(MediaAsset asset) {
    List<ManifestDocument.PageEntry> pages = null;
    if (asset.getType() == MediaType.PDF && asset.getPages() != null) {
      pages =
          asset.getPages().stream()
              .map(page -> new ManifestDocument.PageEntry(page.index(), page.sha256()))
              .toList();
    }
    boolean hasFile = asset.getType() != MediaType.WEB && asset.getType() != MediaType.PDF;
    return new ManifestDocument.AssetEntry(
        asset.getId().toString(),
        asset.getType().name(),
        asset.getName(),
        hasFile ? asset.getSha256() : null,
        asset.getMime(),
        asset.getDurationMs(),
        asset.getUrl(),
        asset.getRefreshSec(),
        pages);
  }

  /** Añade a la lista los ficheros que el reproductor necesita para reproducir el medio. */
  private static void collectFiles(
      MediaAsset asset, Map<String, ManifestDocument.FileEntry> files) {
    switch (asset.getType()) {
      case IMAGE, VIDEO, HTML ->
          files.putIfAbsent(
              asset.getSha256(),
              new ManifestDocument.FileEntry(
                  asset.getSha256(), asset.getSizeBytes(), asset.getMime(), asset.getName()));
      case PDF -> {
        if (asset.getPages() != null) {
          for (PdfPage page : asset.getPages()) {
            files.putIfAbsent(
                page.sha256(),
                new ManifestDocument.FileEntry(
                    page.sha256(),
                    page.sizeBytes(),
                    "image/jpeg",
                    asset.getName() + "-p" + (page.index() + 1) + ".jpg"));
          }
        }
      }
      default -> {}
    }
  }

  /** Convierte una programación en su entrada del manifest. */
  private static ManifestDocument.ScheduleEntry toScheduleEntry(Schedule schedule) {
    return new ManifestDocument.ScheduleEntry(
        schedule.getId().toString(),
        schedule.getPlaylistId().toString(),
        schedule.getPriority(),
        schedule.getStartDate() == null ? null : schedule.getStartDate().toString(),
        schedule.getEndDate() == null ? null : schedule.getEndDate().toString(),
        schedule.getStartTime() == null ? null : schedule.getStartTime().toString(),
        schedule.getEndTime() == null ? null : schedule.getEndTime().toString(),
        List.of(schedule.getDaysOfWeek()),
        schedule.isAllDay(),
        schedule.isEnabled());
  }
}
