package es.cefa.tv.core.aracast.remote;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import es.cefa.tv.core.aracast.client.AracastSshClient;
import es.cefa.tv.core.aracast.exception.AracastException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Terminal interactiva de un Aracast: un shell con PTY abierto por SSH. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AracastShellService {

  private static final int READ_TIMEOUT_SECONDS = 3600;
  private static final int CONNECT_TIMEOUT_MS = 10_000;

  private final AracastSshClient ssh;
  private final Map<UUID, Shell> shells = new ConcurrentHashMap<>();

  /** Quien recibe la salida de un shell y su fin. */
  public interface Listener {

    /**
     * Salida del shell.
     *
     * @param sessionId sesión de terminal
     * @param data texto de salida
     */
    void output(String sessionId, String data);

    /**
     * El shell ha terminado.
     *
     * @param sessionId sesión de terminal
     * @param code código de salida
     */
    void exit(String sessionId, int code);
  }

  /**
   * Abre un shell en el player.
   *
   * @param playerId reproductor
   * @param host IP o nombre del player
   * @param sessionId sesión de terminal
   * @param cols columnas
   * @param rows filas
   * @param listener quien recibe la salida
   */
  public void open(
      UUID playerId, String host, String sessionId, int cols, int rows, Listener listener) {
    close(playerId);
    Session session = ssh.open(host, READ_TIMEOUT_SECONDS);
    try {
      ChannelShell channel = (ChannelShell) session.openChannel("shell");
      channel.setPtyType("xterm-256color", cols, rows, 0, 0);
      Reader reader = new InputStreamReader(channel.getInputStream(), StandardCharsets.UTF_8);
      OutputStream out = channel.getOutputStream();
      channel.connect(CONNECT_TIMEOUT_MS);
      Shell shell = new Shell(session, channel, out);
      shells.put(playerId, shell);
      Thread pump =
          new Thread(() -> pump(playerId, shell, reader, sessionId, listener), "aracast-shell");
      pump.setDaemon(true);
      pump.start();
    } catch (JSchException | IOException e) {
      session.disconnect();
      throw new AracastException("No se pudo abrir la terminal de " + host, e);
    }
  }

  /**
   * Escribe en el shell lo que teclea el usuario.
   *
   * @param playerId reproductor
   * @param data texto
   */
  public void input(UUID playerId, String data) {
    Shell shell = shells.get(playerId);
    if (shell == null) {
      return;
    }
    try {
      shell.out().write(data.getBytes(StandardCharsets.UTF_8));
      shell.out().flush();
    } catch (IOException e) {
      log.debug("No se pudo escribir en la terminal de {}: {}", playerId, e.getMessage());
    }
  }

  /**
   * Cambia el tamaño del terminal.
   *
   * @param playerId reproductor
   * @param cols columnas
   * @param rows filas
   */
  public void resize(UUID playerId, int cols, int rows) {
    Shell shell = shells.get(playerId);
    if (shell != null) {
      shell.channel().setPtySize(cols, rows, 0, 0);
    }
  }

  /**
   * Cierra el shell de un player.
   *
   * @param playerId reproductor
   */
  public void close(UUID playerId) {
    Shell shell = shells.remove(playerId);
    if (shell != null) {
      shell.channel().disconnect();
      shell.session().disconnect();
    }
  }

  private void pump(
      UUID playerId, Shell shell, Reader reader, String sessionId, Listener listener) {
    char[] buffer = new char[4096];
    try {
      int read;
      while ((read = reader.read(buffer)) != -1) {
        listener.output(sessionId, new String(buffer, 0, read));
      }
    } catch (IOException e) {
      log.debug("Terminal de {} cerrado: {}", playerId, e.getMessage());
    }
    if (shells.remove(playerId, shell)) {
      int code = Math.max(0, shell.channel().getExitStatus());
      shell.channel().disconnect();
      shell.session().disconnect();
      listener.exit(sessionId, code);
    }
  }

  private record Shell(Session session, ChannelShell channel, OutputStream out) {}
}
