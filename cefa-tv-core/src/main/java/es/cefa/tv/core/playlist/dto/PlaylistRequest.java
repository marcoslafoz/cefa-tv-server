package es.cefa.tv.core.playlist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Creación o reemplazo de una playlist.
 *
 * @param name nombre
 * @param description descripción opcional
 * @param items elementos en orden
 * @param hidden si es una playlist implícita que no aparece en el listado; si falta se conserva el
 *     valor actual (visible al crear)
 */
public record PlaylistRequest(
    @NotBlank String name,
    String description,
    @Valid List<PlaylistItemRequest> items,
    Boolean hidden) {}
