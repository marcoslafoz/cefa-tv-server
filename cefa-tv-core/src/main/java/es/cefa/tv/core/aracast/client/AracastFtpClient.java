package es.cefa.tv.core.aracast.client;

import es.cefa.tv.core.aracast.config.AracastProperties;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.exception.PlayerOfflineException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.stereotype.Component;

/**
 * Cliente del servidor FTP de los players Aracast. La raíz del FTP es la carpeta temporal del
 * player; los ficheros subidos ahí se mueven a su sitio con los comandos {@code
 * Template.uploadContent}, {@code Template.uploadTemplate} y {@code Player.loadEvents}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AracastFtpClient {

  private final AracastProperties properties;
  private final es.cefa.tv.core.aracast.security.AracastCredentials credentials;

  /**
   * Sube un fichero a la carpeta temporal del player.
   *
   * @param host IP o nombre del player
   * @param fileName nombre con el que se guarda en el player
   * @param content contenido del fichero
   */
  public void upload(String host, String fileName, InputStream content) {
    withClient(
        host,
        ftp -> {
          if (!ftp.storeFile(fileName, content)) {
            throw new AracastException(
                "No se pudo subir " + fileName + " a " + host + ": " + ftp.getReplyString());
          }
          log.debug("Subido {} a {}", fileName, host);
        });
  }

  /**
   * Sube varios ficheros en una sola sesión FTP.
   *
   * @param host IP o nombre del player
   * @param files pares nombre en el player y ruta local
   */
  public void uploadAll(String host, java.util.Map<String, java.nio.file.Path> files) {
    withClient(
        host,
        ftp -> {
          for (var entry : files.entrySet()) {
            try (InputStream in = java.nio.file.Files.newInputStream(entry.getValue())) {
              if (!ftp.storeFile(entry.getKey(), in)) {
                throw new AracastException(
                    "No se pudo subir "
                        + entry.getKey()
                        + " a "
                        + host
                        + ": "
                        + ftp.getReplyString());
              }
            }
            log.debug("Subido {} a {}", entry.getKey(), host);
          }
        });
  }

  /**
   * Descarga un fichero de la carpeta temporal del player.
   *
   * @param host IP o nombre del player
   * @param fileName nombre del fichero
   * @return contenido del fichero
   */
  public byte[] download(String host, String fileName) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    withClient(
        host,
        ftp -> {
          if (!ftp.retrieveFile(fileName, out)) {
            throw new AracastException(
                "No se pudo descargar " + fileName + " de " + host + ": " + ftp.getReplyString());
          }
        });
    return out.toByteArray();
  }

  /**
   * Descarga un fichero de la carpeta temporal del player directamente a disco, sin cargarlo en
   * memoria.
   *
   * @param host IP o nombre del player
   * @param fileName nombre del fichero en el player
   * @param target ruta local donde se guarda
   */
  public void downloadTo(String host, String fileName, java.nio.file.Path target) {
    withClient(
        host,
        ftp -> {
          try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(target)) {
            if (!ftp.retrieveFile(fileName, out)) {
              throw new AracastException(
                  "No se pudo descargar " + fileName + " de " + host + ": " + ftp.getReplyString());
            }
          }
        });
  }

  /**
   * Borra un fichero de la carpeta temporal del player. No falla si el fichero no existe.
   *
   * @param host IP o nombre del player
   * @param fileName nombre del fichero
   */
  public void delete(String host, String fileName) {
    withClient(
        host,
        ftp -> {
          if (!ftp.deleteFile(fileName)) {
            log.debug("No se borró {} en {}: {}", fileName, host, ftp.getReplyString().trim());
          }
        });
  }

  private void withClient(String host, FtpAction action) {
    FTPClient ftp = new FTPClient();
    ftp.setConnectTimeout(properties.commandTimeoutMs());
    ftp.setDefaultTimeout(properties.ftpTimeoutMs());
    ftp.setDataTimeout(java.time.Duration.ofMillis(properties.ftpTimeoutMs()));
    ftp.setBufferSize(1024 * 1024);
    try {
      try {
        ftp.connect(host, properties.ftpPort());
      } catch (IOException e) {
        throw new PlayerOfflineException(
            "El player " + host + " no responde en el puerto FTP " + properties.ftpPort(), e);
      }
      if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
        throw new AracastException("El FTP de " + host + " rechazó la conexión");
      }
      if (!ftp.login(properties.ftpUser(), credentials.ftpPassword(host))) {
        throw new AracastException("Credenciales FTP no válidas para " + host);
      }
      ftp.enterLocalPassiveMode();
      ftp.setFileType(FTP.BINARY_FILE_TYPE);
      action.run(ftp);
      ftp.logout();
    } catch (IOException e) {
      throw new AracastException("Error FTP con el player " + host, e);
    } finally {
      if (ftp.isConnected()) {
        try {
          ftp.disconnect();
        } catch (IOException ignored) {
          log.trace("Error al cerrar la conexión FTP con {}", host);
        }
      }
    }
  }

  /** Operación a ejecutar sobre una sesión FTP ya autenticada. */
  @FunctionalInterface
  private interface FtpAction {
    void run(FTPClient ftp) throws IOException;
  }
}
