package es.cefa.tv.core.usergroup.model;

import es.cefa.tv.core.authorization.model.Permission;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Grupo de usuarios. Es la única unidad de autorización: sus permisos y sus concesiones sobre
 * grupos de reproductores o sedes los heredan todos sus miembros, y los de un usuario son la unión
 * de los de sus grupos.
 */
@Entity
@Table(name = "user_groups")
@Getter
@Setter
@NoArgsConstructor
public class UserGroup {

  /** Identificador del grupo integrado de administradores. */
  public static final UUID ADMINISTRATORS_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000011");

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String name;

  private String description;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "text[]")
  private String[] permissions = new String[0];

  @Column(nullable = false)
  private boolean builtin;

  @Column(name = "all_access", nullable = false)
  private boolean allAccess;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_group_members", joinColumns = @JoinColumn(name = "user_group_id"))
  @Column(name = "user_id")
  private Set<UUID> memberIds = new HashSet<>();

  /**
   * Devuelve los permisos como enumeración.
   *
   * @return los permisos del grupo
   */
  public Set<Permission> permissionSet() {
    Set<Permission> set = EnumSet.noneOf(Permission.class);
    Arrays.stream(permissions).map(Permission::valueOf).forEach(set::add);
    return set;
  }
}
