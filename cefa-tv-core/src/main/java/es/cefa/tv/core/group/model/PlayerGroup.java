package es.cefa.tv.core.group.model;

import es.cefa.tv.core.player.model.IdleLogo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Grupo de reproductores que comparten programación. */
@Entity
@Table(name = "player_groups")
@Getter
@Setter
@NoArgsConstructor
public class PlayerGroup {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(name = "fallback_playlist_id")
  private UUID fallbackPlaylistId;

  @Column(name = "site_id")
  private UUID siteId;

  @Enumerated(EnumType.STRING)
  @Column(name = "idle_logo")
  private IdleLogo idleLogo;

  private String icon;

  private String color;
}
