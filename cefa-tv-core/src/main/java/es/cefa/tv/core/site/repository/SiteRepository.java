package es.cefa.tv.core.site.repository;

import es.cefa.tv.core.site.model.Site;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de sedes. */
public interface SiteRepository extends JpaRepository<Site, UUID> {

  /**
   * Lista todas las sedes por nombre.
   *
   * @return las sedes ordenadas por nombre
   */
  List<Site> findAllByOrderByNameAsc();

  /**
   * Busca una sede por su nombre sin distinguir mayúsculas.
   *
   * @param name nombre
   * @return las sedes con ese nombre (como máximo una)
   */
  List<Site> findByNameIgnoreCase(String name);
}
