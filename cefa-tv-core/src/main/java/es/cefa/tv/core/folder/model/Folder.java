package es.cefa.tv.core.folder.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Carpeta de la biblioteca de medios; una carpeta sin padre cuelga de la raíz. */
@Entity
@Table(name = "folders")
@Getter
@Setter
@NoArgsConstructor
public class Folder {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by")
  private String createdBy;
}
