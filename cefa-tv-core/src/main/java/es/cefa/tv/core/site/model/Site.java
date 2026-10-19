package es.cefa.tv.core.site.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Sede física donde hay reproductores; fija la zona horaria con la que se emite su programación.
 */
@Entity
@Table(name = "sites")
@Getter
@Setter
@NoArgsConstructor
public class Site {

  /** Zona horaria por defecto de una sede nueva. */
  public static final String DEFAULT_TIMEZONE = "Europe/Madrid";

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  private String description;

  private String address;

  private String city;

  private String country;

  @Column(nullable = false)
  private String timezone = DEFAULT_TIMEZONE;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "ip_ranges")
  private List<String> ipRanges;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
