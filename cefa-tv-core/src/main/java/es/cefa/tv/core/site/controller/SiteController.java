package es.cefa.tv.core.site.controller;

import es.cefa.tv.core.site.dto.SiteRequest;
import es.cefa.tv.core.site.dto.SiteResponse;
import es.cefa.tv.core.site.service.SiteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de sedes. Crear, editar y borrar es solo para administradores. */
@RestController
@RequestMapping("/api/v1/sites")
@RequiredArgsConstructor
public class SiteController {

  private final SiteService siteService;

  /**
   * Lista las sedes visibles.
   *
   * @return las sedes con su número de reproductores
   */
  @GetMapping
  public List<SiteResponse> list() {
    return siteService.list();
  }

  /**
   * Obtiene una sede.
   *
   * @param id identificador
   * @return la sede
   */
  @GetMapping("/{id}")
  public SiteResponse get(@PathVariable UUID id) {
    return siteService.get(id);
  }

  /**
   * Crea una sede.
   *
   * @param request datos de la sede
   * @return la sede creada
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SiteResponse create(@Valid @RequestBody SiteRequest request) {
    return siteService.create(request);
  }

  /**
   * Modifica una sede.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return la sede actualizada
   */
  @PutMapping("/{id}")
  public SiteResponse update(@PathVariable UUID id, @Valid @RequestBody SiteRequest request) {
    return siteService.update(id, request);
  }

  /**
   * Borra una sede.
   *
   * @param id identificador
   * @param reassignTo sede a la que pasan sus reproductores, o {@code none} para dejarlos sin sede
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, @RequestParam(required = false) String reassignTo) {
    siteService.delete(id, reassignTo);
  }
}
