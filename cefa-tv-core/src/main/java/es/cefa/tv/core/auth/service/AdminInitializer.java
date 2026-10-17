package es.cefa.tv.core.auth.service;

import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.usergroup.model.UserGroup;
import es.cefa.tv.core.usergroup.repository.UserGroupRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea el administrador inicial si no existe ningún usuario. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

  private final UserRepository users;
  private final UserGroupRepository userGroups;
  private final PasswordEncoder passwordEncoder;
  private final CoreProperties properties;
  private final AuditService audit;

  /**
   * Crea el administrador a partir de {@code ADMIN_USERNAME} y {@code ADMIN_PASSWORD}.
   *
   * @param args argumentos de arranque
   */
  @Override
  public void run(ApplicationArguments args) {
    CoreProperties.Admin admin = properties.admin();
    if (users.count() > 0 || admin.username().isBlank() || admin.password().isBlank()) {
      return;
    }
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername(admin.username());
    user.setDisplayName(admin.username());
    user.setPasswordHash(passwordEncoder.encode(admin.password()));
    user.setEnabled(true);
    user.setCreatedAt(Instant.now());
    users.save(user);
    UserGroup administrators = userGroups.findById(UserGroup.ADMINISTRATORS_ID).orElseThrow();
    administrators.getMemberIds().add(user.getId());
    userGroups.save(administrators);
    audit.recordSystem(
        "USER_CREATE",
        "USER",
        user.getId().toString(),
        user.getUsername(),
        AuditOutcome.SUCCESS,
        Map.of("reason", "INITIAL_ADMIN"));
    log.info("Administrador inicial '{}' creado", admin.username());
  }
}
