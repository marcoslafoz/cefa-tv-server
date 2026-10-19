package es.cefa.tv.core.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaCompressorTest {

  @TempDir Path dir;

  private final FfmpegService ffmpeg = mock(FfmpegService.class);

  private MediaCompressor compressor(boolean enabled) {
    return new MediaCompressor(
        ffmpeg, new MediaCompressionProperties(enabled, 1920, 1080, 4, 23, "fast", "128k", 120, 5));
  }

  private Path file(String name, int bytes) throws IOException {
    Path path = dir.resolve(name);
    Files.write(path, new byte[bytes]);
    return path;
  }

  private void imageOutput(int bytes, boolean ok) {
    when(ffmpeg.compressImage(any(), any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
        .thenAnswer(
            call -> {
              Files.write(call.getArgument(1), new byte[bytes]);
              return ok;
            });
  }

  @Test
  void replacesImageWhenItShrinks() throws IOException {
    imageOutput(500, true);

    var result = compressor(true).compressImage(file("in", 1000), "foto.JPG", dir);

    assertThat(result).isPresent();
    assertThat(result.get().getFileName().toString()).isEqualTo("compressed.jpg");
  }

  @Test
  void keepsPngExtension() throws IOException {
    imageOutput(500, true);

    var result = compressor(true).compressImage(file("in", 1000), "logo.png", dir);

    assertThat(result.orElseThrow().getFileName().toString()).isEqualTo("compressed.png");
  }

  @Test
  void keepsOriginalWhenSavingIsBelowTheMinimum() throws IOException {
    imageOutput(980, true);

    var result = compressor(true).compressImage(file("in", 1000), "foto.jpg", dir);

    assertThat(result).isEmpty();
    assertThat(dir.resolve("compressed.jpg")).doesNotExist();
  }

  @Test
  void keepsOriginalWhenOutputIsBigger() throws IOException {
    imageOutput(2000, true);

    assertThat(compressor(true).compressImage(file("in", 1000), "foto.jpg", dir)).isEmpty();
  }

  @Test
  void keepsOriginalWhenFfmpegFails() throws IOException {
    imageOutput(10, false);

    assertThat(compressor(true).compressImage(file("in", 1000), "foto.jpg", dir)).isEmpty();
  }

  @Test
  void ignoresFormatsItCannotCompress() throws IOException {
    assertThat(compressor(true).compressImage(file("in", 1000), "anim.gif", dir)).isEmpty();
    assertThat(compressor(true).compressImage(file("in", 1000), "foto.webp", dir)).isEmpty();
  }

  @Test
  void doesNothingWhenDisabled() throws IOException {
    assertThat(compressor(false).compressImage(file("in", 1000), "foto.jpg", dir)).isEmpty();
    assertThat(compressor(false).compressVideo(file("v", 1000), dir, p -> {})).isEmpty();
  }

  @Test
  void replacesVideoWhenItShrinks() throws IOException {
    when(ffmpeg.compressVideo(
            any(), any(), anyInt(), anyInt(), anyInt(), anyString(), anyString(), anyInt(), any()))
        .thenAnswer(
            call -> {
              Files.write(call.getArgument(1), new byte[400]);
              return true;
            });

    var result = compressor(true).compressVideo(file("v", 1000), dir, p -> {});

    assertThat(result.orElseThrow().getFileName().toString()).isEqualTo("compressed.mp4");
  }

  @Test
  void renamesToMp4() {
    assertThat(MediaCompressionService.withMp4Extension("clip.mov")).isEqualTo("clip.mp4");
    assertThat(MediaCompressionService.withMp4Extension("clip")).isEqualTo("clip.mp4");
    assertThat(MediaCompressionService.withMp4Extension("a.b.mkv")).isEqualTo("a.b.mp4");
  }
}
