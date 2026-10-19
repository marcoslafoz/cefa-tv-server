package es.cefa.tv.core.aracast.service;

import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.preview.service.PreviewDemandEvent;
import es.cefa.tv.core.preview.service.PreviewService;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Vista previa de los Aracast: mientras alguien mira un Aracast captura su pantalla por SSH cada
 * pocos segundos y entrega la miniatura al servicio de vista previa, que la retransmite igual que
 * la de un reproductor de CEFA TV. Si nadie mira, no se captura nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AracastPreviewService {

  private static final int WIDTH = 320;
  private static final int[] QUALITIES = {12, 24, 31};

  private final AracastPlayerRepository aracasts;
  private final PlayerRepository players;
  private final AracastCaptureService capture;
  private final PreviewService previews;
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
  private final Map<UUID, ScheduledFuture<?>> loops = new ConcurrentHashMap<>();
  private final Map<UUID, AtomicBoolean> busy = new ConcurrentHashMap<>();

  /**
   * Empieza o para la captura de un Aracast según haya o no quien lo mire.
   *
   * @param event cambio de demanda de vista previa
   */
  @EventListener
  public void onDemand(PreviewDemandEvent event) {
    if (aracasts.findById(event.playerId()).isEmpty()) {
      return;
    }
    ScheduledFuture<?> previous = loops.remove(event.playerId());
    if (previous != null) {
      previous.cancel(false);
    }
    if (event.intervalSec() != null) {
      loops.put(
          event.playerId(),
          executor.scheduleWithFixedDelay(
              () -> captureOnce(event.playerId()), 0, event.intervalSec(), TimeUnit.SECONDS));
    }
  }

  void captureOnce(UUID playerId) {
    AtomicBoolean flag = busy.computeIfAbsent(playerId, id -> new AtomicBoolean());
    if (!flag.compareAndSet(false, true)) {
      return;
    }
    try {
      AracastPlayer aracast = aracasts.findById(playerId).orElse(null);
      if (aracast == null) {
        return;
      }
      String resolution = players.findById(playerId).map(p -> p.getResolution()).orElse(null);
      for (int quality : QUALITIES) {
        byte[] jpeg = capture.image(aracast.getHost(), resolution, WIDTH, quality);
        if (jpeg.length <= PreviewService.MAX_FRAME_BYTES) {
          previews.onFrame(playerId, jpeg);
          return;
        }
      }
    } catch (RuntimeException e) {
      log.debug("No se pudo capturar la pantalla del Aracast {}: {}", playerId, e.getMessage());
    } finally {
      flag.set(false);
    }
  }

  /**
   * Indica si se está capturando la pantalla de un Aracast.
   *
   * @param playerId reproductor
   * @return {@code true} si hay un bucle de captura activo
   */
  boolean isCapturing(UUID playerId) {
    return loops.containsKey(playerId);
  }

  /** Para todas las capturas al cerrar la aplicación. */
  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }
}
