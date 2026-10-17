package es.cefa.tv.core.audit.snapshot;

import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.site.model.Site;
import es.cefa.tv.core.site.repository.SiteRepository;
import es.cefa.tv.core.usergroup.model.AccessGrant;
import es.cefa.tv.core.usergroup.model.UserGroup;
import es.cefa.tv.core.usergroup.repository.AccessGrantRepository;
import es.cefa.tv.core.usergroup.repository.UserGroupRepository;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estado auditable de un grupo de usuarios: permisos, acceso total, miembros y reproductores o
 * sedes a los que da acceso.
 */
@Component
@RequiredArgsConstructor
public class UserGroupSnapshot implements AuditSnapshotSource {

  private final UserGroupRepository userGroups;
  private final UserRepository users;
  private final AccessGrantRepository grants;
  private final PlayerGroupRepository playerGroups;
  private final SiteRepository sites;

  @Override
  public String entityType() {
    return "USER_GROUP";
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(UUID id) {
    return userGroups.findById(id).map(this::describe).orElse(null);
  }

  /** Describe un grupo de usuarios. */
  private Map<String, Object> describe(UserGroup group) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("name", group.getName());
    state.put("description", group.getDescription());
    state.put("allAccess", group.isAllAccess());
    state.put("permissions", Arrays.stream(group.getPermissions()).sorted().toList());
    state.put(
        "members",
        users.findAllById(group.getMemberIds()).stream().map(User::getUsername).sorted().toList());
    state.put("grants", grantsOf(group.getId()));
    return state;
  }

  /**
   * Lista las concesiones del grupo como {@code Grupo de reproductores: NIVEL} o {@code Sede: ...}.
   */
  private List<String> grantsOf(UUID userGroupId) {
    return grants.findByUserGroupId(userGroupId).stream()
        .map(this::describeGrant)
        .sorted()
        .toList();
  }

  /** Describe una concesión con el nombre de su destino. */
  private String describeGrant(AccessGrant grant) {
    String target =
        grant.getPlayerGroupId() != null
            ? "Grupo "
                + playerGroups
                    .findById(grant.getPlayerGroupId())
                    .map(PlayerGroup::getName)
                    .orElse("?")
            : "Sede " + sites.findById(grant.getSiteId()).map(Site::getName).orElse("?");
    return target + ": " + grant.getLevel().name();
  }
}
