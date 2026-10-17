package es.cefa.tv.core.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Medio de la biblioteca: imagen, vídeo, PDF, web o HTML. */
@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
public class MediaAsset {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MediaType type;

  private String mime;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  private String sha256;

  @Column(name = "object_key")
  private String objectKey;

  @Column(name = "thumbnail_key")
  private String thumbnailKey;

  @Column(name = "duration_ms")
  private Long durationMs;

  private Integer width;

  private Integer height;

  @Column(name = "page_count")
  private Integer pageCount;

  private String url;

  @Column(name = "refresh_sec")
  private Integer refreshSec;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<PdfPage> pages;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MediaStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "folder_id")
  private UUID folderId;
}
