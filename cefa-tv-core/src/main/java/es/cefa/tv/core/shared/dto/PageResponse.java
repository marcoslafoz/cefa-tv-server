package es.cefa.tv.core.shared.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Página de resultados.
 *
 * @param content elementos de la página
 * @param totalElements total de elementos
 * @param page número de página, empezando en 0
 * @param size tamaño de página
 * @param <T> tipo de elemento
 */
public record PageResponse<T>(List<T> content, long totalElements, int page, int size) {

  /**
   * Convierte una página de Spring Data.
   *
   * @param page página original
   * @param mapper conversión de cada elemento
   * @param <S> tipo origen
   * @param <T> tipo destino
   * @return la página convertida
   */
  public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getTotalElements(),
        page.getNumber(),
        page.getSize());
  }
}
