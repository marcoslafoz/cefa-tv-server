package es.cefa.tv.core.audit.model;

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

/** Entrada del registro de auditoría: una acción de un usuario o del sistema. */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

  @Id private UUID id;

  @Column(nullable = false)
  private Instant at;

  @Column(name = "user_id")
  private UUID userId;

  private String username;

  @Column(nullable = false)
  private String action;

  @Column(name = "entity_type")
  private String entityType;

  @Column(name = "entity_id")
  private String entityId;

  @Column(name = "entity_name")
  private String entityName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuditOutcome outcome;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> details;

  private String ip;

  @Column(name = "user_agent")
  private String userAgent;
}
