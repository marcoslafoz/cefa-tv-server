package es.cefa.tv.core.aracast.management;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Petición de sincronizar la hora de un Aracast.
 *
 * @param mode de dónde se toma la hora
 * @param server servidor NTP para el modo NTP; si se omite se prueban los internos configurados
 */
public record ClockSyncRequest(@NotNull Mode mode, @Size(max = 253) String server) {

  /** Origen de la hora. */
  public enum Mode {
    /** La hora del servidor de CEFA TV, convertida a la zona horaria del player. */
    SERVER,
    /** Un servidor NTP, consultado desde el propio player por SSH. */
    NTP
  }
}
