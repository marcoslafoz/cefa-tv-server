package es.cefa.tv.core.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registro de la entrega de una versión a un reproductor. */
@Entity
@Table(name = "player_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class PlayerDelivery {

  @Id private UUID id;

  @Column(name = "player_id", nullable = false)
  private UUID playerId;

  @Column(nullable = false)
  private long version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DeliveryStatus status;

  private String error;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;
}
