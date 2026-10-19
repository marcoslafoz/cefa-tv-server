package es.cefa.tv.core.aracast.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.core.aracast.config.AracastProperties;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.exception.PlayerOfflineException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cliente de la API de comandos de los players Aracast.
 *
 * <p>El protocolo es una línea JSON por conexión TCP: se envía {@code {"command":{"action": "...",
 * ...}}} terminado en CRLF y el player responde con otra línea JSON terminada en CRLF.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AracastCommandClient {

  private static final byte[] LINE_END = "\r\n".getBytes(StandardCharsets.US_ASCII);
  private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

  private final AracastProperties properties;
  private final ObjectMapper objectMapper;

  /**
   * Envía un comando sin parámetros.
   *
   * @param host IP o nombre del player
   * @param action acción, por ejemplo {@code Player.getVersion}
   * @return nodo {@code command} de la respuesta
   */
  public JsonNode send(String host, String action) {
    return send(host, action, Map.of());
  }

  /**
   * Envía un comando con parámetros adicionales.
   *
   * @param host IP o nombre del player
   * @param action acción, por ejemplo {@code Template.getContent}
   * @param params parámetros que se añaden junto a {@code action} dentro de {@code command}
   * @return nodo {@code command} de la respuesta
   * @throws AracastException si no se puede conectar, la respuesta no es JSON válido o el player
   *     devuelve {@code "result":"fail"}
   */
  public JsonNode send(String host, String action, Map<String, Object> params) {
    ObjectNode command = objectMapper.createObjectNode();
    command.put("action", action);
    params.forEach((key, value) -> command.set(key, objectMapper.valueToTree(value)));
    ObjectNode envelope = objectMapper.createObjectNode();
    envelope.set("command", command);

    String request;
    try {
      request = objectMapper.writeValueAsString(envelope);
    } catch (IOException e) {
      throw new AracastException("No se pudo serializar el comando " + action, e);
    }

    log.debug("Enviando a {}: {}", host, request);
    String raw = exchange(host, request);
    log.debug("Respuesta de {}: {}", host, abbreviate(raw));

    JsonNode response;
    try {
      response = objectMapper.readTree(raw);
    } catch (IOException e) {
      throw new AracastException("Respuesta no válida del player " + host + " a " + action, e);
    }
    JsonNode commandNode = response.path("command");
    if (commandNode.isMissingNode()) {
      throw new AracastException("Respuesta sin nodo command del player " + host + " a " + action);
    }
    JsonNode result = commandNode.path("result");
    if (result.isTextual() && "fail".equals(result.asText())) {
      throw new AracastException("El player " + host + " rechazó el comando " + action);
    }
    return commandNode;
  }

  private String exchange(String host, String request) {
    int timeout = properties.commandTimeoutMs();
    try (Socket socket = new Socket()) {
      try {
        socket.connect(new InetSocketAddress(host, properties.commandPort()), timeout);
      } catch (IOException e) {
        throw new PlayerOfflineException(
            "El player " + host + " no responde en el puerto " + properties.commandPort(), e);
      }
      socket.setSoTimeout(timeout);
      OutputStream out = socket.getOutputStream();
      out.write(request.getBytes(StandardCharsets.UTF_8));
      out.write(LINE_END);
      out.flush();
      return readLine(socket.getInputStream());
    } catch (IOException e) {
      throw new AracastException(
          "No se pudo comunicar con el player " + host + ":" + properties.commandPort(), e);
    }
  }

  /**
   * Comprueba si el player acepta conexiones en el puerto de comandos, sin enviar nada.
   *
   * @param host IP o nombre del player
   * @param timeoutMs tiempo máximo de espera de la conexión
   */
  public boolean isReachable(String host, int timeoutMs) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, properties.commandPort()), timeoutMs);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static String readLine(InputStream in) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = in.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
      if (buffer.size() > MAX_RESPONSE_BYTES) {
        throw new IOException("Respuesta demasiado grande");
      }
      if (endsWithLineEnd(buffer)) {
        break;
      }
    }
    return buffer.toString(StandardCharsets.UTF_8).trim();
  }

  private static boolean endsWithLineEnd(ByteArrayOutputStream buffer) {
    byte[] bytes = buffer.toByteArray();
    int n = bytes.length;
    return n >= 2 && bytes[n - 2] == '\r' && bytes[n - 1] == '\n';
  }

  private static String abbreviate(String text) {
    return text.length() <= 300 ? text : text.substring(0, 300) + "...";
  }
}
