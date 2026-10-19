package es.cefa.tv.core.aracast.ics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Programación completa de un player.
 *
 * @param name nombre descriptivo (se guarda en {@code X-ARACAST-SCHEDULE})
 * @param entries franjas de la programación
 * @param unsupportedEvents número de eventos del calendario que CEFA TV no sabe editar todavía
 *     (comandos serie o TCP/IP); solo informativo en lectura
 */
public record IcsSchedule(
    @NotBlank String name, @NotNull List<@Valid IcsEntry> entries, int unsupportedEvents) {}
