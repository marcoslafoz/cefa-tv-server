package es.cefa.tv.core.microsoft.dto;

import java.util.Locale;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Identidad de Microsoft Entra ID extraída del token de identificación.
 *
 * @param email correo normalizado en minúsculas, o {@code null} si el token no trae ninguno
 * @param objectId identificador del objeto del usuario en el directorio ({@code oid}), o {@code
 *     null}
 * @param displayName nombre visible, o {@code null}
 */
public record MicrosoftIdentity(String email, String objectId, String displayName) {

  /**
   * Construye la identidad a partir de las reclamaciones del token. El correo sale de {@code
   * email}, {@code preferred_username} o {@code upn}, en ese orden, y solo si tiene forma de
   * dirección.
   *
   * @param user usuario OpenID Connect autenticado
   * @return la identidad
   */
  public static MicrosoftIdentity from(OidcUser user) {
    String email = null;
    for (String claim : new String[] {"email", "preferred_username", "upn"}) {
      String candidate = normalize(user.getClaimAsString(claim));
      if (candidate != null && candidate.contains("@")) {
        email = candidate;
        break;
      }
    }
    String objectId = blankToNull(user.getClaimAsString("oid"));
    return new MicrosoftIdentity(email, objectId, blankToNull(user.getFullName()));
  }

  private static String normalize(String value) {
    String trimmed = blankToNull(value);
    return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
