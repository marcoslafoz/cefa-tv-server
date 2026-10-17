package es.cefa.tv.core.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.manifest.model.ManifestDocument;
import es.cefa.tv.core.manifest.service.ManifestAssembler;
import es.cefa.tv.core.media.model.MediaAsset;
import es.cefa.tv.core.media.model.MediaType;
import es.cefa.tv.core.media.model.PdfPage;
import es.cefa.tv.core.playlist.model.Fit;
import es.cefa.tv.core.playlist.model.Playlist;
import es.cefa.tv.core.playlist.model.PlaylistItem;
import es.cefa.tv.core.schedule.model.Schedule;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas del ensamblado determinista del manifest. */
class ManifestAssemblerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private MediaAsset asset(MediaType type, String sha) {
    MediaAsset asset = new MediaAsset();
    asset.setId(UUID.randomUUID());
    asset.setName(type.name().toLowerCase());
    asset.setType(type);
    asset.setSha256(sha);
    asset.setMime(type == MediaType.VIDEO ? "video/mp4" : "image/png");
    asset.setSizeBytes(100);
    return asset;
  }

  private Playlist playlist(MediaAsset... assets) {
    Playlist playlist = new Playlist();
    playlist.setId(UUID.randomUUID());
    playlist.setName("lista");
    int position = 0;
    for (MediaAsset asset : assets) {
      PlaylistItem item = new PlaylistItem();
      item.setId(UUID.randomUUID());
      item.setPlaylist(playlist);
      item.setPosition(position++);
      item.setAsset(asset);
      item.setDurationSec(10);
      item.setFit(Fit.COVER);
      playlist.getItems().add(item);
    }
    return playlist;
  }

  private Schedule schedule(Playlist playlist) {
    Schedule schedule = new Schedule();
    schedule.setId(UUID.randomUUID());
    schedule.setName("s");
    schedule.setPlaylistId(playlist.getId());
    schedule.setPriority(1);
    schedule.setStartTime(LocalTime.of(8, 0));
    schedule.setEndTime(LocalTime.of(20, 0));
    schedule.setDaysOfWeek(new Integer[] {1, 2, 3});
    return schedule;
  }

  @Test
  void samePlaylistReferencedTwiceProducesOneFileEntry() {
    MediaAsset image = asset(MediaType.IMAGE, "aaa");
    Playlist first = playlist(image);
    Playlist second = playlist(image);

    ManifestDocument document =
        ManifestAssembler.assemble(
            UUID.randomUUID(), "UTC", null, List.of(schedule(first)), List.of(first, second));

    assertThat(document.files()).hasSize(1);
    assertThat(document.assets()).hasSize(1);
    assertThat(document.playlists()).hasSize(2);
  }

  @Test
  void pdfContributesOneFilePerPageAndNoOriginal() {
    MediaAsset pdf = asset(MediaType.PDF, "pdfsha");
    pdf.setPages(List.of(new PdfPage(0, "k0", "p0", 10), new PdfPage(1, "k1", "p1", 20)));

    ManifestDocument document =
        ManifestAssembler.assemble(
            UUID.randomUUID(), "UTC", null, List.of(), List.of(playlist(pdf)));

    assertThat(document.files())
        .extracting(ManifestDocument.FileEntry::sha256)
        .containsExactly("p0", "p1");
    assertThat(document.assets().values().iterator().next().pages()).hasSize(2);
    assertThat(document.assets().values().iterator().next().sha256()).isNull();
  }

  @Test
  void webAssetsCarryTheUrlAndNoFiles() {
    MediaAsset web = asset(MediaType.WEB, null);
    web.setUrl("https://example.com");

    ManifestDocument document =
        ManifestAssembler.assemble(
            UUID.randomUUID(), "UTC", null, List.of(), List.of(playlist(web)));

    assertThat(document.files()).isEmpty();
    assertThat(document.assets().values().iterator().next().url()).isEqualTo("https://example.com");
  }

  @Test
  void serializationIsDeterministic() throws Exception {
    UUID playerId = UUID.randomUUID();
    MediaAsset image = asset(MediaType.IMAGE, "aaa");
    MediaAsset video = asset(MediaType.VIDEO, "bbb");
    Playlist list = playlist(image, video);
    Schedule schedule = schedule(list);

    String one =
        mapper.writeValueAsString(
            ManifestAssembler.assemble(playerId, "UTC", null, List.of(schedule), List.of(list)));
    String two =
        mapper.writeValueAsString(
            ManifestAssembler.assemble(playerId, "UTC", null, List.of(schedule), List.of(list)));

    assertThat(one).isEqualTo(two);
  }
}
