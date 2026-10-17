package es.cefa.tv.core.playerapi.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Confirmación de aplicación de una versión.
 *
 * @param version versión
 * @param status {@code APPLIED} o {@code FAILED}
 * @param error motivo del fallo
 */
public record AckRequest(@NotNull Long version, @NotNull AckStatus status, String error) {

  /** Resultado de aplicar una versión. */
  public enum AckStatus {
    /** Aplicada correctamente. */
    APPLIED,
    /** No se pudo aplicar. */
    FAILED
  }
}
