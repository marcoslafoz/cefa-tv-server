package es.cefa.tv.core.media.repository;

import es.cefa.tv.core.media.model.StoredFile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de ficheros descargables por los reproductores. */
public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

  /**
   * Busca los ficheros de un medio.
   *
   * @param assetId identificador del medio
   * @return los ficheros del medio
   */
  List<StoredFile> findByAssetId(UUID assetId);

  /**
   * Busca ficheros por su resumen.
   *
   * @param sha256 resúmenes buscados
   * @return los ficheros encontrados, que pueden repetir resumen si hay contenido duplicado
   */
  List<StoredFile> findBySha256In(Collection<String> sha256);
}
