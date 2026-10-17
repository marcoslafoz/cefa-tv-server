package es.cefa.tv.core.audit.snapshot;

import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.usergroup.model.UserGroup;
import es.cefa.tv.core.usergroup.repository.UserGroupRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Estado auditable de un usuario, sin contraseña ni identificadores de cuenta externa. */
@Component
@RequiredArgsConstructor
public class UserSnapshot implements AuditSnapshotSource {

  private final UserRepository users;
  private final UserGroupRepository userGroups;

  @Override
  public String entityType() {
    return "USER";
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(UUID id) {
    return users.findById(id).map(this::describe).orElse(null);
  }

  /** Describe un usuario con sus grupos. */
  private Map<String, Object> describe(User user) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("username", user.getUsername());
    state.put("displayName", user.getDisplayName());
    state.put("email", user.getEmail());
    state.put("authProvider", user.getAuthProvider().name());
    state.put("enabled", user.isEnabled());
    state.put(
        "groups",
        userGroups.findByMember(user.getId()).stream().map(UserGroup::getName).sorted().toList());
    return state;
  }
}
