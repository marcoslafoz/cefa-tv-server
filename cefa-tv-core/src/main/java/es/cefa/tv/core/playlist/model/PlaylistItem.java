package es.cefa.tv.core.playlist.model;

import es.cefa.tv.core.media.model.MediaAsset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Elemento de una playlist: un medio con su duración y ajustes. */
@Entity
@Table(name = "playlist_items")
@Getter
@Setter
@NoArgsConstructor
public class PlaylistItem {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "playlist_id")
  private Playlist playlist;

  @Column(nullable = false)
  private int position;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "asset_id")
  private MediaAsset asset;

  @Column(name = "duration_sec", nullable = false)
  private int durationSec;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Fit fit = Fit.COVER;

  @Column(nullable = false)
  private boolean muted = true;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> settings;
}
