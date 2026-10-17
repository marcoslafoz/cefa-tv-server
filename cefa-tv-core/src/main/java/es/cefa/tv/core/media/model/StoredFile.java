package es.cefa.tv.core.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fichero físico que un reproductor puede descargar identificándolo por su SHA-256. */
@Entity
@Table(name = "stored_files")
@Getter
@Setter
@NoArgsConstructor
public class StoredFile {

  @Id private UUID id;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(nullable = false)
  private String sha256;

  @Column(name = "object_key", nullable = false)
  private String objectKey;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(nullable = false)
  private String mime;

  @Column(nullable = false)
  private String name;
}
