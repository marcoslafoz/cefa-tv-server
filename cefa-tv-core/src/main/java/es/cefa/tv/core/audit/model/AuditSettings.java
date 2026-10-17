package es.cefa.tv.core.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ajustes de la auditoría; hay una única fila. */
@Entity
@Table(name = "audit_settings")
@Getter
@Setter
@NoArgsConstructor
public class AuditSettings {

  /** Identificador de la fila única de ajustes. */
  public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

  /** Retención mínima permitida, en días. */
  public static final int MIN_RETENTION_DAYS = 30;

  /** Retención máxima permitida, en días. */
  public static final int MAX_RETENTION_DAYS = 3650;

  @Id private UUID id;

  @Column(name = "retention_days", nullable = false)
  private int retentionDays;
}
