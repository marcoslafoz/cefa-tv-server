package es.cefa.tv.core.aracast.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros de conexión con los players Aracast. Son comunes a todos los players: cada uno se
 * identifica solo por su host.
 *
 * @param commandPort puerto TCP de la API de comandos JSON (5401 por defecto)
 * @param commandTimeoutMs tiempo máximo de espera de conexión y lectura de un comando
 * @param ftpPort puerto del servidor FTP del player (5481 por defecto)
 * @param ftpUser usuario del FTP del player
 * @param ftpPassword contraseña del FTP del player
 * @param ftpTimeoutMs tiempo máximo sin actividad durante una transferencia FTP (vídeos grandes)
 * @param sshUser usuario SSH de los players (siempre el mismo)
 * @param sshPasswords contraseñas SSH posibles; se prueban por orden hasta que una funciona
 * @param sshTimeoutMs tiempo máximo de conexión SSH
 */
@ConfigurationProperties(prefix = "cefa.tv.aracast")
public record AracastProperties(
    int commandPort,
    int commandTimeoutMs,
    int ftpPort,
    String ftpUser,
    String ftpPassword,
    int ftpTimeoutMs,
    String sshUser,
    List<String> sshPasswords,
    int sshTimeoutMs) {

  /** Contraseñas SSH sin vacíos ni espacios sobrantes. */
  public List<String> cleanSshPasswords() {
    return sshPasswords == null
        ? List.of()
        : sshPasswords.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
  }
}
