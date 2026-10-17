package es.cefa.tv.core.delivery.service;

import es.cefa.tv.core.delivery.dto.DeliveryResponse;
import es.cefa.tv.core.delivery.model.DeliveryStatus;
import es.cefa.tv.core.delivery.model.PlayerDelivery;
import es.cefa.tv.core.delivery.repository.PlayerDeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Seguimiento del estado de entrega de cada versión a cada reproductor. */
@Service
@RequiredArgsConstructor
public class DeliveryService {

  private static final Set<DeliveryStatus> OPEN =
      Set.of(DeliveryStatus.QUEUED, DeliveryStatus.DOWNLOADING);

  private final PlayerDeliveryRepository deliveries;

  /**
   * Registra una versión nueva en cola.
   *
   * @param playerId reproductor
   * @param version versión calculada
   */
  @Transactional
  public void queue(UUID playerId, long version) {
    Instant now = Instant.now();
    PlayerDelivery delivery = new PlayerDelivery();
    delivery.setId(UUID.randomUUID());
    delivery.setPlayerId(playerId);
    delivery.setVersion(version);
    delivery.setStatus(DeliveryStatus.QUEUED);
    delivery.setCreatedAt(now);
    delivery.setUpdatedAt(now);
    deliveries.save(delivery);
  }

  /**
   * Marca como descargando una versión en cola.
   *
   * @param playerId reproductor
   * @param version versión que se descarga
   */
  @Transactional
  public void markDownloading(UUID playerId, long version) {
    deliveries
        .findByPlayerIdAndVersion(playerId, version)
        .filter(delivery -> delivery.getStatus() == DeliveryStatus.QUEUED)
        .ifPresent(
            delivery -> {
              delivery.setStatus(DeliveryStatus.DOWNLOADING);
              delivery.setUpdatedAt(Instant.now());
            });
  }

  /**
   * Cierra como aplicadas todas las entregas abiertas hasta la versión indicada.
   *
   * @param playerId reproductor
   * @param version versión aplicada
   */
  @Transactional
  public void markApplied(UUID playerId, long version) {
    Instant now = Instant.now();
    deliveries
        .findByPlayerIdAndVersionLessThanEqualAndStatusIn(playerId, version, OPEN)
        .forEach(
            delivery -> {
              delivery.setStatus(DeliveryStatus.APPLIED);
              delivery.setError(null);
              delivery.setUpdatedAt(now);
              delivery.setDeliveredAt(now);
            });
  }

  /**
   * Marca como fallida una versión.
   *
   * @param playerId reproductor
   * @param version versión
   * @param error motivo
   */
  @Transactional
  public void markFailed(UUID playerId, long version, String error) {
    deliveries
        .findByPlayerIdAndVersion(playerId, version)
        .ifPresent(
            delivery -> {
              delivery.setStatus(DeliveryStatus.FAILED);
              delivery.setError(error);
              delivery.setUpdatedAt(Instant.now());
            });
  }

  /**
   * Lista el historial de un reproductor.
   *
   * @param playerId reproductor
   * @return las entregas, la más reciente primero
   */
  @Transactional(readOnly = true)
  public List<DeliveryResponse> history(UUID playerId) {
    return deliveries.findTop100ByPlayerIdOrderByVersionDesc(playerId).stream()
        .map(
            d ->
                new DeliveryResponse(
                    d.getVersion(),
                    d.getStatus(),
                    d.getError(),
                    d.getCreatedAt(),
                    d.getUpdatedAt(),
                    d.getDeliveredAt()))
        .toList();
  }
}
