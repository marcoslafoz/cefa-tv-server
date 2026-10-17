package es.cefa.tv.core.group.dto;

import es.cefa.tv.core.player.model.IdleLogo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Creación o modificación de un grupo.
 *
 * @param name nombre
 * @param fallbackPlaylistId playlist de reposo del grupo, opcional
 * @param icon nombre de un icono de Tabler en PascalCase y sin prefijo de librería, como {@code
 *     IconBuildingStore}; si es {@code null} al modificar se conserva y una cadena vacía lo quita
 * @param color color del catálogo ({@code slate}, {@code blue}, {@code green}, {@code amber},
 *     {@code red}, {@code violet}, {@code pink} o {@code teal}); si es {@code null} al modificar se
 *     conserva y una cadena vacía lo quita
 * @param siteId sede a la que pertenece el grupo; al fijarla, todos sus reproductores pasan a ella
 * @param clearSite quita la sede del grupo sin cambiar la de sus reproductores
 * @param idleLogo logo de la pantalla sin programación para los reproductores del grupo que no
 *     tienen uno propio
 * @param clearIdleLogo quita el logo del grupo
 */
public record GroupRequest(
    @NotBlank String name,
    UUID fallbackPlaylistId,
    @Size(max = 64)
        @Pattern(
            regexp = "^(Icon[A-Za-z0-9]+)?$",
            message = "Debe ser un icono de Tabler, como IconBuildingStore")
        String icon,
    @Pattern(
            regexp = "^(slate|blue|green|amber|red|violet|pink|teal)?$",
            message = "Debe ser uno de: slate, blue, green, amber, red, violet, pink, teal")
        String color,
    UUID siteId,
    Boolean clearSite,
    IdleLogo idleLogo,
    Boolean clearIdleLogo) {}
