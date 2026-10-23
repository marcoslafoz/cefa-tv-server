package es.cefa.tv.core.audit.service;

import es.cefa.tv.core.audit.dto.AuditActionResponse;
import java.util.List;

/** Catálogo de las acciones auditadas conocidas, para poblar el filtro de la interfaz. */
public final class AuditActionCatalog {

  /** Acciones conocidas con el tipo de entidad que afectan. */
  public static final List<AuditActionResponse> KNOWN =
      List.of(
          new AuditActionResponse("ACCESS_DENIED", null),
          new AuditActionResponse("AUTH_LOGIN", "USER"),
          new AuditActionResponse("AUTH_LOGIN_MICROSOFT", "USER"),
          new AuditActionResponse("AUTH_LOGOUT", "USER"),
          new AuditActionResponse("AUTH_PASSWORD_CHANGE", "USER"),
          new AuditActionResponse("USER_CREATE", "USER"),
          new AuditActionResponse("USER_UPDATE", "USER"),
          new AuditActionResponse("USER_DELETE", "USER"),
          new AuditActionResponse("USER_PASSWORD_RESET", "USER"),
          new AuditActionResponse("USER_GROUP_CREATE", "USER_GROUP"),
          new AuditActionResponse("USER_GROUP_UPDATE", "USER_GROUP"),
          new AuditActionResponse("USER_GROUP_DELETE", "USER_GROUP"),
          new AuditActionResponse("MEDIA_UPLOAD", "MEDIA"),
          new AuditActionResponse("MEDIA_CREATE_WEB", "MEDIA"),
          new AuditActionResponse("MEDIA_UPDATE", "MEDIA"),
          new AuditActionResponse("MEDIA_DELETE", "MEDIA"),
          new AuditActionResponse("PLAYLIST_CREATE", "PLAYLIST"),
          new AuditActionResponse("PLAYLIST_UPDATE", "PLAYLIST"),
          new AuditActionResponse("PLAYLIST_DELETE", "PLAYLIST"),
          new AuditActionResponse("SCHEDULE_CREATE", "SCHEDULE"),
          new AuditActionResponse("SCHEDULE_UPDATE", "SCHEDULE"),
          new AuditActionResponse("SCHEDULE_DELETE", "SCHEDULE"),
          new AuditActionResponse("SITE_CREATE", "SITE"),
          new AuditActionResponse("SITE_UPDATE", "SITE"),
          new AuditActionResponse("SITE_DELETE", "SITE"),
          new AuditActionResponse("PLAYER_SITE_ASSIGN", "PLAYER"),
          new AuditActionResponse("FOLDER_CREATE", "FOLDER"),
          new AuditActionResponse("FOLDER_RENAME", "FOLDER"),
          new AuditActionResponse("FOLDER_MOVE", "FOLDER"),
          new AuditActionResponse("FOLDER_DELETE", "FOLDER"),
          new AuditActionResponse("MEDIA_MOVE", "MEDIA"),
          new AuditActionResponse("PLAYER_GROUP_CREATE", "PLAYER_GROUP"),
          new AuditActionResponse("PLAYER_GROUP_UPDATE", "PLAYER_GROUP"),
          new AuditActionResponse("PLAYER_GROUP_DELETE", "PLAYER_GROUP"),
          new AuditActionResponse("GROUP_SITE_ASSIGN", "PLAYER_GROUP"),
          new AuditActionResponse("GROUP_IDLE_LOGO_CHANGE", "PLAYER_GROUP"),
          new AuditActionResponse("PAIRING_CLAIM", "PLAYER"),
          new AuditActionResponse("ARACAST_PUBLISH", "PLAYER"),
          new AuditActionResponse("PLAYER_ARACAST_CREATE", "PLAYER"),
          new AuditActionResponse("SITE_ARACAST_DISCOVER", "SITE"),
          new AuditActionResponse("PLAYER_ARACAST_CREDENTIALS", "PLAYER"),
          new AuditActionResponse("PLAYER_UPDATE", "PLAYER"),
          new AuditActionResponse("PLAYER_DELETE", "PLAYER"),
          new AuditActionResponse("PLAYER_AUTO_DELETE", "PLAYER"),
          new AuditActionResponse("PLAYER_RESYNC", "PLAYER"),
          new AuditActionResponse("PLAYER_RESTART", "PLAYER"),
          new AuditActionResponse("PLAYER_RELOAD", "PLAYER"),
          new AuditActionResponse("PLAYER_DISPLAY_REDETECT", "PLAYER"),
          new AuditActionResponse("PLAYER_DISPLAY_RESOLUTION", "PLAYER"),
          new AuditActionResponse("PLAYER_OUTPUT_CHANGE", "PLAYER"),
          new AuditActionResponse("PLAYER_VOLUME_CHANGE", "PLAYER"),
          new AuditActionResponse("PLAYER_IDLE_LOGO_CHANGE", "PLAYER"),
          new AuditActionResponse("PLAYER_REMOTE_START", "PLAYER"),
          new AuditActionResponse("PLAYER_REMOTE_STOP", "PLAYER"),
          new AuditActionResponse("PLAYER_SHELL_OPEN", "PLAYER"),
          new AuditActionResponse("PLAYER_SHELL_CLOSE", "PLAYER"),
          new AuditActionResponse("AUDIT_EXPORT", "SETTINGS"),
          new AuditActionResponse("AUDIT_SETTINGS_UPDATE", "SETTINGS"),
          new AuditActionResponse("AUDIT_PURGE", "SETTINGS"));

  /** Evita la instanciación de la clase de utilidades. */
  private AuditActionCatalog() {}
}
