package es.cefa.tv.core.manifest.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Versión de la programación calculada para un reproductor. */
@Entity
@Table(name = "player_manifests")
@IdClass(PlayerManifestId.class)
@Getter
@Setter
@NoArgsConstructor
public class PlayerManifest {

  @Id
  @Column(name = "player_id")
  private UUID playerId;

  @Id private long version;

  @Column(name = "content_hash", nullable = false)
  private String contentHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private ManifestDocument manifest;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
