package es.cefa.tv.core.pairing.model;

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

/** Registro de un intento de emparejamiento por parte de un usuario. */
@Entity
@Table(name = "pairing_audits")
@Getter
@Setter
@NoArgsConstructor
public class PairingAudit {

  /** Resultado del intento. */
  public enum Outcome {
    /** El código era válido y se emparejó el reproductor. */
    SUCCESS,
    /** El código no existía o había caducado. */
    INVALID,
    /** Se rechazó por exceso de intentos erróneos. */
    RATE_LIMITED
  }

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String username;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Outcome outcome;

  @Column(name = "player_id")
  private UUID playerId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
