package es.cefa.tv.core.player.dto;

import es.cefa.tv.core.player.model.IdleLogo;
import es.cefa.tv.core.player.model.PlayerStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Modificación de un reproductor; los campos nulos no se tocan.
 *
 * @param name nuevo nombre
 * @param groupId nuevo grupo
 * @param clearGroup quita el grupo del reproductor
 * @param fallbackPlaylistId nueva playlist de reposo
 * @param clearFallbackPlaylist quita la playlist de reposo propia
 * @param orientation nueva orientación
 * @param status nuevo estado administrativo
 * @param preferredOutput salida de vídeo elegida; debe figurar entre las conocidas si las hay
 * @param clearPreferredOutput vuelve a la selección automática de salida
 * @param description nueva descripción; una cadena vacía la borra
 * @param siteId nueva sede
 * @param clearSite quita la sede del reproductor
 * @param volume volumen del sonido de los vídeos, de 0 a 100
 * @param idleLogo logo propio de la pantalla sin programación
 * @param clearIdleLogo quita el logo propio para que herede el de su grupo o el de por defecto
 */
public record PlayerUpdateRequest(
    String name,
    UUID groupId,
    Boolean clearGroup,
    UUID fallbackPlaylistId,
    Boolean clearFallbackPlaylist,
    String orientation,
    PlayerStatus status,
    String preferredOutput,
    Boolean clearPreferredOutput,
    @Size(max = 500) String description,
    UUID siteId,
    Boolean clearSite,
    @Min(0) @Max(100) Integer volume,
    IdleLogo idleLogo,
    Boolean clearIdleLogo) {}
