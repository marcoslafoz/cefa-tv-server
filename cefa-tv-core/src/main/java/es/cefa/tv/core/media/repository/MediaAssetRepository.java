package es.cefa.tv.core.media.repository;

import es.cefa.tv.core.media.model.MediaAsset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Repositorio de medios. */
public interface MediaAssetRepository
    extends JpaRepository<MediaAsset, UUID>, JpaSpecificationExecutor<MediaAsset> {

  /**
   * Suma el tamaño de todos los medios.
   *
   * @return el total en bytes
   */
  @Query("select coalesce(sum(m.sizeBytes), 0) from MediaAsset m")
  long totalSizeBytes();

  /**
   * Cuenta los medios de cada carpeta.
   *
   * @return una fila {@code [carpeta, total]} por cada carpeta con medios
   */
  @Query(
      "select m.folderId as folderId, count(m) as total from MediaAsset m"
          + " where m.folderId is not null group by m.folderId")
  List<es.cefa.tv.core.folder.repository.FolderCount> countByFolder();

  /**
   * Indica si una carpeta contiene medios.
   *
   * @param folderId carpeta
   * @return {@code true} si contiene alguno
   */
  boolean existsByFolderId(UUID folderId);

  /**
   * Mueve todos los medios de una carpeta a otra.
   *
   * @param from carpeta de origen
   * @param to carpeta de destino, o {@code null} para la raíz
   * @return el número de medios movidos
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update MediaAsset m set m.folderId = :to where m.folderId = :from")
  int moveFolderContents(UUID from, UUID to);
}
