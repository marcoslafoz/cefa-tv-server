package es.cefa.tv.core.player.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Pantalla (receptor) registrada en el sistema. */
@Entity
@Table(name = "players")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Player {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "group_id")
  private UUID groupId;

  @Column(name = "site_id")
  private UUID siteId;

  private String description;

  @Column(name = "fallback_playlist_id")
  private UUID fallbackPlaylistId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PlayerStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PlayerKind kind = PlayerKind.CEFA_TV;

  @Column(name = "device_token_hash", unique = true)
  private String deviceTokenHash;

  private String hostname;

  private String ip;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> hardware;

  @Column(name = "system_info")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> systemInfo;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<Map<String, Object>> displays;

  private String resolution;

  @Column(name = "preferred_output")
  private String preferredOutput;

  private int volume = 100;

  @Enumerated(EnumType.STRING)
  @Column(name = "idle_logo")
  private IdleLogo idleLogo;

  private String orientation;

  @Column(name = "player_version")
  private String playerVersion;

  @Column(name = "player_build")
  private String playerBuild;

  private String timezone;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Column(name = "desired_version", nullable = false)
  private long desiredVersion;

  @Column(name = "applied_version", nullable = false)
  private long appliedVersion;

  private String state;

  @Column(name = "current_playlist_id")
  private UUID currentPlaylistId;

  @Column(name = "current_asset_id")
  private UUID currentAssetId;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "first_connected_at")
  private Instant firstConnectedAt;

  /**
   * Registra un contacto del reproductor y, si es el primero, la fecha de primera conexión.
   *
   * @param now instante del contacto
   */
  public void markSeen(Instant now) {
    lastSeenAt = now;
    if (firstConnectedAt == null) {
      firstConnectedAt = now;
    }
  }

  /**
   * Indica si hay una versión de programación pendiente de aplicar.
   *
   * @return {@code true} si la versión deseada es mayor que la aplicada
   */
  public boolean isPending() {
    return desiredVersion > appliedVersion;
  }
}
