package es.cefa.tv.core.auth.model;

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

/** Usuario de la interfaz de administración, local, de Microsoft o híbrido. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "password_hash")
  private String passwordHash;

  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_provider", nullable = false)
  private AuthProvider authProvider = AuthProvider.LOCAL;

  @Column(name = "microsoft_object_id")
  private String microsoftObjectId;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
