package es.cefa.tv.core.manifest.service;

import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Agrupa los cambios de contenido y recalcula los manifests tras una pausa (debounce). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManifestRebuildCoordinator {

  private static final long CLEAN = 0L;

  private final ManifestService manifestService;
  private final CoreProperties properties;
  private final AtomicLong lastChangeNanos = new AtomicLong(CLEAN);

  /**
   * Anota que el contenido ha cambiado; se ejecuta tras confirmar la transacción que lo provocó.
   *
   * @param event evento de cambio
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onContentChanged(ContentChangedEvent event) {
    lastChangeNanos.set(System.nanoTime());
    log.debug("Cambio de contenido: {}", event.reason());
  }

  /** Comprueba periódicamente si ha pasado el tiempo de espera desde el último cambio. */
  @Scheduled(fixedDelay = 250)
  public void flushIfQuiet() {
    long changedAt = lastChangeNanos.get();
    if (changedAt == CLEAN) {
      return;
    }
    long quietMs = (System.nanoTime() - changedAt) / 1_000_000;
    if (quietMs >= properties.manifest().debounceMs()
        && lastChangeNanos.compareAndSet(changedAt, CLEAN)) {
      rebuildAll();
    }
  }

  /** Recalcula el manifest de todos los reproductores activos sin esperar al debounce. */
  public void rebuildAll() {
    for (UUID playerId : manifestService.activePlayerIds()) {
      try {
        manifestService.rebuild(playerId, false);
      } catch (RuntimeException e) {
        log.error("No se pudo recalcular el manifest del reproductor {}", playerId, e);
      }
    }
  }
}
