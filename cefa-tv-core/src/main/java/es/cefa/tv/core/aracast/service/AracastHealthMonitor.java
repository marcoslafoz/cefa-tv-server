package es.cefa.tv.core.aracast.service;

import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.dto.PlayerStatus;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Comprueba cada medio minuto qué Aracast responden y refresca sus datos. Equivale al latido de un
 * reproductor de CEFA TV: mantiene {@code lastSeenAt}, con el que se calcula si está en línea.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AracastHealthMonitor {

  private static final int REACHABLE_TIMEOUT_MS = 3000;
  private static final int PARALLELISM = 8;

  private final AracastPlayerRepository aracasts;
  private final AracastCommandClient commands;
  private final AracastQueryService query;
  private final AracastPlayerService service;
  private final ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);

  /** Tarea periódica de comprobación. */
  @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
  public void check() {
    List<Callable<Void>> tasks =
        aracasts.findAll().stream().<Callable<Void>>map(a -> () -> refresh(a)).toList();
    try {
      executor.invokeAll(tasks);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Void refresh(AracastPlayer aracast) {
    try {
      if (!commands.isReachable(aracast.getHost(), REACHABLE_TIMEOUT_MS)) {
        return null;
      }
      PlayerStatus status = query.getStatus(aracast.getHost());
      service.applyStatus(aracast.getPlayerId(), status);
    } catch (RuntimeException e) {
      log.debug("No se pudo refrescar el Aracast {}: {}", aracast.getHost(), e.getMessage());
    }
    return null;
  }

  /** Cierra el pool al parar la aplicación. */
  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }
}
