package es.cefa.tv.core.auth.model;

/** Forma en la que un usuario puede iniciar sesión. */
public enum AuthProvider {

  /** Solo con usuario y contraseña. */
  LOCAL,

  /** Solo con Microsoft Entra ID; no tiene contraseña. */
  MICROSOFT,

  /** Con usuario y contraseña o con Microsoft Entra ID. */
  HYBRID;

  /**
   * Indica si el usuario puede entrar con el formulario de usuario y contraseña.
   *
   * @return {@code true} si usa contraseña
   */
  public boolean allowsPassword() {
    return this != MICROSOFT;
  }

  /**
   * Indica si el usuario puede entrar con Microsoft.
   *
   * @return {@code true} si usa Microsoft
   */
  public boolean allowsMicrosoft() {
    return this != LOCAL;
  }
}
