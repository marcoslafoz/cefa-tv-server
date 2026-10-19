package es.cefa.tv.core.aracast.client;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import es.cefa.tv.core.aracast.config.AracastProperties;
import es.cefa.tv.core.aracast.exception.AracastAuthException;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.exception.PlayerOfflineException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cliente SSH de los players. El usuario es siempre el mismo y puede haber más de una contraseña;
 * se prueban por orden y se recuerda cuál funciona en cada player para no repetir intentos fallidos
 * (los players de la serie 4 llevan fail2ban).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AracastSshClient {

  private static final int MAX_OUTPUT_BYTES = 64 * 1024 * 1024;

  private final AracastProperties properties;
  private final es.cefa.tv.core.aracast.security.AracastCredentials credentials;
  private final ConcurrentMap<String, String> workingPassword = new ConcurrentHashMap<>();

  /**
   * Ejecuta un comando y devuelve su salida estándar.
   *
   * @param host IP o nombre del player
   * @param command comando de shell
   * @param timeoutSeconds tiempo máximo de ejecución
   * @return bytes de la salida estándar
   * @throws AracastException si no hay contraseñas, falla la autenticación o el comando termina con
   *     error
   */
  public byte[] exec(String host, String command, int timeoutSeconds) {
    return execOn(connect(host, timeoutSeconds), host, command);
  }

  /**
   * Ejecuta un comando autenticándose con una contraseña concreta, sin probar las demás ni
   * recordarla. Sirve para validar la contraseña que se acaba de escribir.
   *
   * @param host IP o nombre del player
   * @param password contraseña SSH a probar
   * @param command comando de shell
   * @param timeoutSeconds tiempo máximo de ejecución
   * @return texto de la salida estándar
   * @throws AracastAuthException si el player rechaza la contraseña
   */
  public String execTextWithPassword(
      String host, String password, String command, int timeoutSeconds) {
    Session session = connectWith(host, password, timeoutSeconds);
    return new String(execOn(session, host, command), StandardCharsets.UTF_8).trim();
  }

  private byte[] execOn(Session session, String host, String command) {
    try {
      ChannelExec channel = (ChannelExec) session.openChannel("exec");
      ByteArrayOutputStream err = new ByteArrayOutputStream();
      channel.setCommand(command);
      channel.setInputStream(null);
      channel.setErrStream(err);
      InputStream in = channel.getInputStream();
      channel.connect(properties.sshTimeoutMs());
      byte[] output = readAll(in);
      long deadline = System.currentTimeMillis() + 5000;
      while (!channel.isClosed() && System.currentTimeMillis() < deadline) {
        Thread.sleep(50);
      }
      int status = channel.getExitStatus();
      channel.disconnect();
      if (status != 0) {
        String detail = err.toString(StandardCharsets.UTF_8).trim();
        if (detail.isEmpty()) {
          detail = new String(output, StandardCharsets.UTF_8).trim();
        }
        throw new AracastException(
            "El comando falló en " + host + " (código " + status + "): " + abbreviate(detail));
      }
      return output;
    } catch (JSchException | IOException e) {
      throw new AracastException("Error SSH con el player " + host + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AracastException("Operación SSH interrumpida", e);
    } finally {
      session.disconnect();
    }
  }

  /** Igual que {@link #exec} pero devolviendo el texto de la salida. */
  public String execText(String host, String command, int timeoutSeconds) {
    return new String(exec(host, command, timeoutSeconds), StandardCharsets.UTF_8).trim();
  }

  /**
   * Abre una sesión SSH que queda a cargo de quien la pide (debe cerrarla con {@code disconnect}).
   * Sirve para canales de larga duración, como el terminal o la captura continua.
   *
   * @param host IP o nombre del player
   * @param readTimeoutSeconds segundos sin tráfico tras los que se da la conexión por perdida
   */
  public Session open(String host, int readTimeoutSeconds) {
    Session session = connect(host, readTimeoutSeconds);
    try {
      session.setServerAliveInterval(15000);
    } catch (JSchException e) {
      log.debug("No se pudo activar el keep-alive SSH con {}", host);
    }
    return session;
  }

  private Session connectWith(String host, String password, int execTimeoutSeconds) {
    try {
      JSch jsch = new JSch();
      Session session = jsch.getSession(properties.sshUser(), host, 22);
      session.setPassword(password);
      session.setConfig("StrictHostKeyChecking", "no");
      session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
      session.setTimeout(execTimeoutSeconds * 1000);
      session.connect(properties.sshTimeoutMs());
      return session;
    } catch (JSchException e) {
      if (isAuthFailure(e)) {
        throw new AracastAuthException("El player " + host + " rechazó la contraseña SSH", e);
      }
      if (isUnreachable(e)) {
        throw new PlayerOfflineException("El player " + host + " no responde por SSH", e);
      }
      throw new AracastException("No se pudo abrir SSH con " + host + ": " + e.getMessage(), e);
    }
  }

  private Session connect(String host, int execTimeoutSeconds) {
    List<String> passwords = orderedPasswords(host);
    if (passwords.isEmpty()) {
      throw new AracastException("No hay contraseñas SSH configuradas para " + host);
    }
    JSchException lastAuthFailure = null;
    for (String password : passwords) {
      try {
        JSch jsch = new JSch();
        Session session = jsch.getSession(properties.sshUser(), host, 22);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
        session.setTimeout(execTimeoutSeconds * 1000);
        session.connect(properties.sshTimeoutMs());
        workingPassword.put(host.toLowerCase(), password);
        return session;
      } catch (JSchException e) {
        if (isAuthFailure(e)) {
          lastAuthFailure = e;
          log.debug("Contraseña SSH rechazada por {}", host);
          continue;
        }
        if (isUnreachable(e)) {
          throw new PlayerOfflineException("El player " + host + " no responde por SSH", e);
        }
        throw new AracastException("No se pudo abrir SSH con " + host + ": " + e.getMessage(), e);
      }
    }
    workingPassword.remove(host.toLowerCase());
    throw new AracastException(
        "Ninguna de las contraseñas SSH configuradas es válida para " + host, lastAuthFailure);
  }

  private List<String> orderedPasswords(String host) {
    List<String> all = new ArrayList<>(credentials.sshPasswords(host));
    String known = workingPassword.get(host.toLowerCase());
    if (known != null && all.remove(known)) {
      all.add(0, known);
    }
    return all;
  }

  private static boolean isAuthFailure(JSchException e) {
    String m = e.getMessage() == null ? "" : e.getMessage();
    return m.contains("Auth fail") || m.contains("Auth cancel");
  }

  private static boolean isUnreachable(JSchException e) {
    Throwable cause = e.getCause();
    return cause instanceof ConnectException
        || cause instanceof SocketTimeoutException
        || cause instanceof NoRouteToHostException
        || cause instanceof UnknownHostException
        || (e.getMessage() != null && e.getMessage().contains("timeout"));
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[64 * 1024];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
      if (out.size() > MAX_OUTPUT_BYTES) {
        throw new IOException("La salida del comando es demasiado grande");
      }
    }
    return out.toByteArray();
  }

  private static String abbreviate(String text) {
    return text.length() <= 300 ? text : text.substring(0, 300) + "...";
  }
}
