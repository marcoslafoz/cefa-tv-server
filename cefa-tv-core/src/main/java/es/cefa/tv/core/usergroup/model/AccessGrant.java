package es.cefa.tv.core.usergroup.model;

import es.cefa.tv.core.authorization.model.AccessLevel;
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

/** Concesión de un nivel de acceso sobre un grupo de reproductores a un usuario o a un grupo. */
@Entity
@Table(name = "access_grants")
@Getter
@Setter
@NoArgsConstructor
public class AccessGrant {

  @Id private UUID id;

  @Column(name = "user_group_id", nullable = false)
  private UUID userGroupId;

  @Column(name = "player_group_id")
  private UUID playerGroupId;

  @Column(name = "site_id")
  private UUID siteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccessLevel level;
}
