package es.cefa.tv.core.aracast.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Datos de conexión de un reproductor de tipo Aracast. */
@Entity
@Table(name = "aracast_players")
@Getter
@Setter
@NoArgsConstructor
public class AracastPlayer {

  @Id
  @Column(name = "player_id")
  private UUID playerId;

  @Column(nullable = false, unique = true)
  private String host;

  private String mac;

  private String model;

  @Column(name = "ssh_password_enc")
  private String sshPasswordEnc;

  @Column(name = "ftp_password_enc")
  private String ftpPasswordEnc;

  @Column(name = "published_hash")
  private String publishedHash;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
