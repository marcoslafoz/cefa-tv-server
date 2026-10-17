package es.cefa.tv.core.playerapi.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Petición de un lote de ficheros.
 *
 * @param sha256 resúmenes de los ficheros pedidos
 */
public record FilesRequest(@NotEmpty @Size(max = 500) List<String> sha256) {}
