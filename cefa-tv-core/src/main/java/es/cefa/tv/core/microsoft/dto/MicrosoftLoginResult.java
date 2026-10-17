package es.cefa.tv.core.microsoft.dto;

import es.cefa.tv.core.auth.model.User;

/**
 * Resultado de resolver una identidad de Microsoft contra los usuarios locales.
 *
 * @param user usuario con el que se inicia sesión, o {@code null} si se rechaza
 * @param errorCode código de rechazo que se envía a la interfaz en {@code ?error=}, o {@code null}
 */
public record MicrosoftLoginResult(User user, String errorCode) {

  /**
   * Resultado correcto.
   *
   * @param user usuario autenticado
   * @return el resultado
   */
  public static MicrosoftLoginResult accepted(User user) {
    return new MicrosoftLoginResult(user, null);
  }

  /**
   * Resultado de rechazo.
   *
   * @param errorCode código de error
   * @return el resultado
   */
  public static MicrosoftLoginResult rejected(String errorCode) {
    return new MicrosoftLoginResult(null, errorCode);
  }

  /**
   * Indica si el inicio de sesión fue aceptado.
   *
   * @return {@code true} si hay usuario
   */
  public boolean isAccepted() {
    return user != null;
  }
}
