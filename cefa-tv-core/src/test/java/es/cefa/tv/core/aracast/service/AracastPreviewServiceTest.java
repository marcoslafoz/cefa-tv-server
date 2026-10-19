package es.cefa.tv.core.aracast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.preview.service.PreviewDemandEvent;
import es.cefa.tv.core.preview.service.PreviewService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AracastPreviewServiceTest {

  private final UUID id = UUID.randomUUID();
  private AracastPlayerRepository aracasts;
  private AracastCaptureService capture;
  private PreviewService previews;
  private AracastPreviewService service;

  @BeforeEach
  void setUp() {
    aracasts = mock(AracastPlayerRepository.class);
    capture = mock(AracastCaptureService.class);
    previews = mock(PreviewService.class);
    AracastPlayer aracast = new AracastPlayer();
    aracast.setPlayerId(id);
    aracast.setHost("10.0.0.5");
    when(aracasts.findById(id)).thenReturn(Optional.of(aracast));
    service = new AracastPreviewService(aracasts, mock(PlayerRepository.class), capture, previews);
  }

  @Test
  void capturesAndDeliversAFrame() {
    byte[] jpeg = new byte[] {1, 2, 3};
    when(capture.image(eq("10.0.0.5"), any(), anyInt(), anyInt())).thenReturn(jpeg);

    service.captureOnce(id);

    verify(previews).onFrame(id, jpeg);
  }

  @Test
  void retriesWithLowerQualityWhenTheFrameIsTooBig() {
    byte[] big = new byte[PreviewService.MAX_FRAME_BYTES + 1];
    byte[] small = new byte[] {9};
    when(capture.image(eq("10.0.0.5"), any(), anyInt(), eq(12))).thenReturn(big);
    when(capture.image(eq("10.0.0.5"), any(), anyInt(), eq(24))).thenReturn(small);

    service.captureOnce(id);

    verify(previews).onFrame(id, small);
    verify(capture, never()).image(any(), any(), anyInt(), eq(31));
  }

  @Test
  void aFailedCaptureDoesNotReachThePreviewNorBreakTheLoop() {
    when(capture.image(any(), any(), anyInt(), anyInt())).thenThrow(new RuntimeException("SSH"));

    service.captureOnce(id);

    verify(previews, never()).onFrame(any(), any());
  }

  @Test
  void startsTheLoopWhenSomeoneWatchesAndStopsItWhenNobodyDoes() {
    when(capture.image(any(), any(), anyInt(), anyInt())).thenReturn(new byte[] {1});

    service.onDemand(new PreviewDemandEvent(id, 5));
    assertThat(service.isCapturing(id)).isTrue();

    service.onDemand(new PreviewDemandEvent(id, null));
    assertThat(service.isCapturing(id)).isFalse();
    service.shutdown();
  }

  @Test
  void ignoresPlayersThatAreNotAracast() {
    UUID other = UUID.randomUUID();
    when(aracasts.findById(other)).thenReturn(Optional.empty());

    service.onDemand(new PreviewDemandEvent(other, 5));

    verify(capture, never()).image(any(), any(), anyInt(), anyInt());
  }

  @Test
  void readsTheScreenSizeFromTheResolutionText() {
    assertThat(AracastCaptureService.parseSize("1920x1080 @60.0")).isEqualTo("1920x1080");
    assertThat(AracastCaptureService.parseSize("8x8")).isEqualTo("1920x1080");
    assertThat(AracastCaptureService.parseSize(null)).isEqualTo("1920x1080");
  }
}
