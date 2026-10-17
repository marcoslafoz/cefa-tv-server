package es.cefa.tv.core.shell.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registro de auditoría de una sesión de terminal remota; no guarda su contenido. */
@Entity
@Table(name = "shell_audits")
@Getter
@Setter
@NoArgsConstructor
public class ShellAudit {

  @Id private UUID id;

  @Column(name = "player_id", nullable = false)
  private UUID playerId;

  @Column(name = "player_name", nullable = false)
  private String playerName;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String username;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "bytes_in", nullable = false)
  private long bytesIn;

  @Column(name = "bytes_out", nullable = false)
  private long bytesOut;

  @Column(name = "close_reason")
  private String closeReason;
}
