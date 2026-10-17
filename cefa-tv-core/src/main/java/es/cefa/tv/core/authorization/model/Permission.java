package es.cefa.tv.core.authorization.model;

/** Permisos que se pueden conceder a un rol. */
public enum Permission {
  /** Subir, renombrar y borrar medios de la biblioteca. */
  MEDIA_MANAGE("Gestionar medios de la biblioteca"),
  /** Crear, editar y borrar playlists. */
  PLAYLIST_MANAGE("Gestionar playlists"),
  /** Crear, editar y borrar programaciones. */
  SCHEDULE_MANAGE("Gestionar programaciones"),
  /** Ver reproductores, su estado, métricas y entregas. */
  PLAYER_VIEW("Ver reproductores"),
  /** Emparejar, editar, reiniciar y dar de baja reproductores. */
  PLAYER_MANAGE("Gestionar reproductores"),
  /** Controlar la pantalla de un reproductor con ratón y teclado. */
  PLAYER_REMOTE("Control remoto de pantallas"),
  /** Abrir una terminal en un reproductor. */
  PLAYER_TERMINAL("Terminal remota en reproductores"),
  /** Gestionar usuarios, roles, grupos de usuarios y concesiones. */
  USER_MANAGE("Gestionar usuarios, roles y permisos"),
  /** Consultar la auditoría de la actividad de los usuarios. */
  AUDIT_VIEW("Ver la auditoría de actividad");

  private final String description;

  Permission(String description) {
    this.description = description;
  }

  /**
   * Devuelve la descripción legible del permiso.
   *
   * @return la descripción
   */
  public String getDescription() {
    return description;
  }
}
