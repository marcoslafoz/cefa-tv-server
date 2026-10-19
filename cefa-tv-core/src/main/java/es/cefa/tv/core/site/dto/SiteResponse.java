package es.cefa.tv.core.site.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sede con el número de reproductores.
 *
 * @param id identificador
 * @param name nombre
 * @param description descripción
 * @param address dirección
 * @param city ciudad
 * @param country país
 * @param timezone zona horaria IANA
 * @param ipRanges rangos de direcciones de la sede, vacío si no tiene
 * @param playerCount reproductores de la sede que ve el usuario
 * @param onlineCount de ellos, los que están en línea
 * @param createdAt fecha de creación
 */
public record SiteResponse(
    UUID id,
    String name,
    String description,
    String address,
    String city,
    String country,
    String timezone,
    List<String> ipRanges,
    long playerCount,
    long onlineCount,
    Instant createdAt) {}
