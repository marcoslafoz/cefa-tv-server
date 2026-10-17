package es.cefa.tv.core.delivery.model;

/** Estado de la entrega de una versión de programación a un reproductor. */
public enum DeliveryStatus {
  /** Calculada y pendiente de que el reproductor la descargue. */
  QUEUED,
  /** El reproductor la está descargando. */
  DOWNLOADING,
  /** El reproductor la ha aplicado. */
  APPLIED,
  /** El reproductor no pudo aplicarla. */
  FAILED
}
