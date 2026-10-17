package es.cefa.tv.core.player.model;

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

/** Orden pendiente de entregar a un reproductor desconectado. */
@Entity
@Table(name = "player_commands")
@Getter
@Setter
@NoArgsConstructor
public class PlayerCommand {

  @Id private UUID id;

  @Column(name = "player_id", nullable = false)
  private UUID playerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CommandType type;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;
}
