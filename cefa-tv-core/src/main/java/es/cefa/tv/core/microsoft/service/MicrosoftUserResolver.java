package es.cefa.tv.core.microsoft.service;

import es.cefa.tv.core.auth.model.AuthProvider;
import es.cefa.tv.core.auth.model.User;
import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.microsoft.dto.MicrosoftIdentity;
import es.cefa.tv.core.microsoft.dto.MicrosoftLoginResult;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.usergroup.repository.UserGroupRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vincula una identidad de Microsoft con un usuario local. Primero busca por el identificador de
 * objeto ya enlazado y, si no hay, por correo sin distinguir mayúsculas; el enlace por correo
 * guarda el identificador de objeto para que un cambio posterior de correo no permita suplantar la
 * cuenta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MicrosoftUserResolver {

  /** Código: el token no trae ningún correo utilizable. */
  public static final String EMAIL_MISSING = "microsoft_email_missing";

  /** Código: no existe un usuario con ese correo y el alta automática está desactivada. */
  public static final String USER_NOT_FOUND = "microsoft_user_not_found";

  /** Código: el usuario está desactivado. */
  public static final String USER_DISABLED = "microsoft_user_disabled";

  /** Código: el usuario existe pero solo puede entrar con contraseña. */
  public static final String NOT_ALLOWED = "microsoft_not_allowed";

  /** Código: el correo ya está enlazado a otra cuenta de Microsoft o el nombre de usuario choca. */
  public static final String ACCOUNT_MISMATCH = "microsoft_account_mismatch";

  private final UserRepository users;
  private final CoreProperties properties;
  private final UserGroupRepository userGroups;

  /**
   * Resuelve el usuario local de una identidad de Microsoft, creándolo si el alta automática está
   * activada.
   *
   * @param identity identidad extraída del token
   * @return el usuario aceptado o el código de rechazo
   */
  @Transactional
  public MicrosoftLoginResult resolve(MicrosoftIdentity identity) {
    Optional<User> byObjectId =
        identity.objectId() == null
            ? Optional.empty()
            : users.findByMicrosoftObjectId(identity.objectId());
    if (byObjectId.isPresent()) {
      return check(byObjectId.get());
    }
    if (identity.email() == null) {
      return MicrosoftLoginResult.rejected(EMAIL_MISSING);
    }
    Optional<User> byEmail = users.findByEmailIgnoreCase(identity.email());
    if (byEmail.isPresent()) {
      return bindAndCheck(byEmail.get(), identity);
    }
    if (!properties.microsoft().autoProvision()) {
      log.info("Inicio de sesión de Microsoft rechazado: no hay usuario para {}", identity.email());
      return MicrosoftLoginResult.rejected(USER_NOT_FOUND);
    }
    return provision(identity);
  }

  /** Enlaza el identificador de objeto al usuario hallado por correo, si no tenía otro. */
  private MicrosoftLoginResult bindAndCheck(User user, MicrosoftIdentity identity) {
    if (user.getMicrosoftObjectId() != null
        && !user.getMicrosoftObjectId().equals(identity.objectId())) {
      return MicrosoftLoginResult.rejected(ACCOUNT_MISMATCH);
    }
    MicrosoftLoginResult result = check(user);
    if (result.isAccepted() && identity.objectId() != null) {
      user.setMicrosoftObjectId(identity.objectId());
    }
    return result;
  }

  /** Comprueba que el usuario esté activo y pueda entrar con Microsoft. */
  private MicrosoftLoginResult check(User user) {
    if (!user.isEnabled()) {
      return MicrosoftLoginResult.rejected(USER_DISABLED);
    }
    if (!user.getAuthProvider().allowsMicrosoft()) {
      return MicrosoftLoginResult.rejected(NOT_ALLOWED);
    }
    return MicrosoftLoginResult.accepted(user);
  }

  /**
   * Crea un usuario de solo Microsoft sin permisos: no pertenece a ningún grupo salvo el indicado
   * en {@code MICROSOFT_DEFAULT_GROUP_ID}, que nunca puede dar acceso total.
   */
  private MicrosoftLoginResult provision(MicrosoftIdentity identity) {
    if (users.existsByUsernameIgnoreCase(identity.email())) {
      return MicrosoftLoginResult.rejected(ACCOUNT_MISMATCH);
    }
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername(identity.email());
    user.setDisplayName(identity.displayName() == null ? identity.email() : identity.displayName());
    user.setEmail(identity.email());
    user.setAuthProvider(AuthProvider.MICROSOFT);
    user.setMicrosoftObjectId(identity.objectId());
    user.setEnabled(true);
    user.setCreatedAt(Instant.now());
    users.save(user);
    addToDefaultGroup(user);
    log.info("Usuario '{}' creado automáticamente desde Microsoft", identity.email());
    return MicrosoftLoginResult.accepted(user);
  }

  /** Añade el usuario al grupo por defecto configurado, si es válido y no da acceso total. */
  private void addToDefaultGroup(User user) {
    String raw = properties.microsoft().defaultGroupId();
    if (raw == null || raw.isBlank()) {
      return;
    }
    try {
      userGroups
          .findById(UUID.fromString(raw.trim()))
          .filter(group -> !group.isAllAccess())
          .ifPresentOrElse(
              group -> group.getMemberIds().add(user.getId()),
              () ->
                  log.warn(
                      "MICROSOFT_DEFAULT_GROUP_ID no es un grupo válido sin acceso total;"
                          + " el usuario se crea sin grupos"));
    } catch (IllegalArgumentException e) {
      log.warn("MICROSOFT_DEFAULT_GROUP_ID no es un identificador válido; se ignora");
    }
  }
}
