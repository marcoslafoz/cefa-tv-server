package es.cefa.tv.core.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Creación o modificación de una sede.
 *
 * @param name nombre, único sin distinguir mayúsculas
 * @param description descripción opcional
 * @param address dirección opcional
 * @param city ciudad opcional
 * @param country país opcional
 * @param timezone zona horaria IANA; si falta, {@code Europe/Madrid}
 * @param ipRanges rangos de direcciones de la sede (CIDR, intervalo o IP suelta), que usa el
 *     descubrimiento de players Aracast; opcional
 */
public record SiteRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 500) String description,
    @Size(max = 300) String address,
    @Size(max = 100) String city,
    @Size(max = 100) String country,
    @Size(max = 64) String timezone,
    List<@Size(max = 64) String> ipRanges) {}
