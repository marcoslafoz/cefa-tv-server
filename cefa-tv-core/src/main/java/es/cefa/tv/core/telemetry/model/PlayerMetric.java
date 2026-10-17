package es.cefa.tv.core.telemetry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Muestra periódica de carga y recursos de un reproductor. */
@Entity
@Table(name = "player_metrics")
@Getter
@Setter
@NoArgsConstructor
public class PlayerMetric {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "player_id", nullable = false)
  private UUID playerId;

  @Column(nullable = false)
  private Instant at;

  @Column(name = "cpu_percent")
  private Double cpuPercent;

  @Column(name = "ram_used_mb")
  private Long ramUsedMb;

  @Column(name = "disk_free_mb")
  private Long diskFreeMb;

  @Column(name = "temp_c")
  private Double tempC;
}
