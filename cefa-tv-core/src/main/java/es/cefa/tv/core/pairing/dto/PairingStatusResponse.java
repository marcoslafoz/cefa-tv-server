package es.cefa.tv.core.pairing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Estado de una petición de emparejamiento. Al aprobarse incluye las credenciales del reproductor.
 *
 * @param status {@code PENDING}, {@code EXPIRED} o {@code APPROVED}
 * @param playerId reproductor creado, solo cuando está aprobada
 * @param deviceToken token de dispositivo, solo cuando está aprobada
 * @param name nombre asignado al reproductor, solo cuando está aprobada
 * @param pollIntervalSec segundos entre heartbeats, solo cuando está aprobada
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PairingStatusResponse(
    String status, UUID playerId, String deviceToken, String name, Integer pollIntervalSec) {

  /**
   * Respuesta sin credenciales.
   *
   * @param status estado
   * @return la respuesta
   */
  public static PairingStatusResponse of(String status) {
    return new PairingStatusResponse(status, null, null, null, null);
  }
}
