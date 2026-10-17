package es.cefa.tv.core.folder.repository;

import es.cefa.tv.core.folder.model.Folder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de carpetas de la biblioteca de medios. */
public interface FolderRepository extends JpaRepository<Folder, UUID> {

  /**
   * Lista todas las carpetas.
   *
   * @return las carpetas ordenadas por nombre
   */
  List<Folder> findAllByOrderByNameAsc();

  /**
   * Lista las carpetas que cuelgan directamente de otra.
   *
   * @param parentId carpeta padre
   * @return las subcarpetas
   */
  List<Folder> findByParentId(UUID parentId);

  /**
   * Lista las carpetas de primer nivel, que cuelgan directamente de la raíz.
   *
   * @return las carpetas de primer nivel
   */
  List<Folder> findByParentIdIsNull();

  /**
   * Indica si una carpeta tiene subcarpetas.
   *
   * @param parentId carpeta padre
   * @return {@code true} si hay alguna
   */
  boolean existsByParentId(UUID parentId);
}
