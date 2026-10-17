package es.cefa.tv.core.pairing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Petición de emparejamiento de un reproductor que muestra un código en su pantalla. */
@Entity
@Table(name = "pairings")
@Getter
@Setter
@NoArgsConstructor
public class Pairing {

  @Id private UUID id;

  @Column(nullable = false)
  private String code;

  @Column(name = "secret_hash", nullable = false)
  private String secretHash;

  private String hostname;

  private String ip;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> hardware;

  private String resolution;

  @Column(name = "player_version")
  private String playerVersion;

  @Column(name = "player_build")
  private String playerBuild;

  private String timezone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PairingStatus status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "player_id")
  private UUID playerId;

  @Column(name = "approved_by")
  private String approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
