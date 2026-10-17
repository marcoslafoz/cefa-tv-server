package es.cefa.tv.core.group.dto;

import es.cefa.tv.core.player.model.IdleLogo;
import java.util.UUID;

/**
 * Grupo de reproductores.
 *
 * @param id identificador
 * @param name nombre
 * @param fallbackPlaylistId playlist de reposo
 * @param playerCount número de reproductores del grupo
 * @param icon icono de Tabler en PascalCase sin prefijo, o {@code null}
 * @param color color del catálogo, o {@code null}
 * @param siteId sede del grupo; si la tiene, es la de todos sus reproductores
 * @param siteName nombre de la sede del grupo
 * @param idleLogo logo de la pantalla sin programación del grupo, o {@code null} si no define uno
 */
public record GroupResponse(
    UUID id,
    String name,
    UUID fallbackPlaylistId,
    long playerCount,
    String icon,
    String color,
    UUID siteId,
    String siteName,
    IdleLogo idleLogo) {}
