package es.cefa.tv.core.aracast.service;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.dto.PlayerContent;
import es.cefa.tv.core.aracast.dto.PlayerStatus;
import es.cefa.tv.core.aracast.dto.PlayerTemplate;
import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Consultas de solo lectura sobre un player Aracast. */
@Service
@RequiredArgsConstructor
public class AracastQueryService {

  private static final DateTimeFormatter PLAYER_CLOCK =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final AracastCommandClient commandClient;

  /**
   * Estado resumido del player.
   *
   * @param host IP o nombre del player
   */
  public PlayerStatus getStatus(String host) {
    JsonNode node = commandClient.send(host, "Player.getVersion");
    JsonNode templates = node.path("templates");
    JsonNode mainInterface = firstInterfaceWithIp(node.path("real_networks").path("if"));
    JsonNode clock =
        commandClient.send(host, "System.getConfiguration").path("result").path("clock");
    String playerTime = text(clock, "datetime");
    String timezone = text(clock, "timezone");
    return new PlayerStatus(
        host,
        text(node, "result"),
        text(node, "model"),
        text(node, "mac"),
        mainInterface == null ? null : text(mainInterface, "ip"),
        text(node, "uptime"),
        text(node.path("resolutions"), "current"),
        text(templates, "current"),
        text(templates, "next"),
        text(templates, "next_time"),
        text(templates, "default"),
        longValue(node, "totalbytes"),
        longValue(node, "freebytes"),
        playerTime,
        timezone,
        clockOffsetSeconds(playerTime, timezone));
  }

  /**
   * Calcula el desfase del reloj del player comparando su hora local con la hora actual en su zona
   * horaria.
   */
  static Long clockOffsetSeconds(String playerTime, String timezone) {
    if (playerTime == null || timezone == null) {
      return null;
    }
    try {
      LocalDateTime player = LocalDateTime.parse(playerTime, PLAYER_CLOCK);
      LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
      return Duration.between(now, player).toSeconds();
    } catch (DateTimeException e) {
      return null;
    }
  }

  /**
   * Plantillas instaladas en el player, ordenadas por nombre.
   *
   * @param host IP o nombre del player
   */
  public List<PlayerTemplate> getTemplates(String host) {
    JsonNode files = commandClient.send(host, "Template.getTemplates").path("result").path("files");
    List<PlayerTemplate> templates = new ArrayList<>();
    for (Iterator<Map.Entry<String, JsonNode>> it = files.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode t = entry.getValue();
      List<String> elements = new ArrayList<>();
      t.path("content").forEach(e -> elements.add(e.asText()));
      templates.add(
          new PlayerTemplate(
              text(t, "name"),
              entry.getKey(),
              text(t, "hash"),
              t.path("size").asLong(),
              epochSeconds(t, "mtime"),
              elements,
              usableThumbnail(text(t, "thumb"))));
    }
    templates.sort(Comparator.comparing(PlayerTemplate::name, String.CASE_INSENSITIVE_ORDER));
    return templates;
  }

  /**
   * Contenidos instalados en el player, ordenados por nombre.
   *
   * @param host IP o nombre del player
   */
  public List<PlayerContent> getContents(String host) {
    JsonNode files = commandClient.send(host, "Template.getContent").path("result").path("files");
    List<PlayerContent> contents = new ArrayList<>();
    for (Iterator<Map.Entry<String, JsonNode>> it = files.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode c = entry.getValue();
      contents.add(
          new PlayerContent(
              text(c, "name"),
              entry.getKey(),
              text(c, "hash"),
              c.path("size").asLong(),
              epochSeconds(c, "mtime")));
    }
    contents.sort(Comparator.comparing(PlayerContent::name, String.CASE_INSENSITIVE_ORDER));
    return contents;
  }

  /**
   * Envía un comando arbitrario y devuelve la respuesta tal cual.
   *
   * @param host IP o nombre del player
   * @param action acción del comando
   * @param params parámetros adicionales
   */
  public JsonNode sendRaw(String host, String action, Map<String, Object> params) {
    return commandClient.send(host, action, params == null ? Map.of() : params);
  }

  /**
   * Devuelve la miniatura solo si es una imagen con contenido. Los players 2.x devuelven un PNG de
   * 0x0 píxeles cuando no generan miniatura.
   */
  static String usableThumbnail(String dataUri) {
    if (dataUri == null || !dataUri.startsWith("data:image/")) {
      return null;
    }
    String prefix = "data:image/png;base64,";
    if (!dataUri.startsWith(prefix)) {
      return dataUri;
    }
    try {
      byte[] png = Base64.getDecoder().decode(dataUri.substring(prefix.length()));
      if (png.length < 24) {
        return null;
      }
      ByteBuffer header = ByteBuffer.wrap(png, 16, 8);
      return header.getInt() > 0 && header.getInt() > 0 ? dataUri : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static JsonNode firstInterfaceWithIp(JsonNode interfaces) {
    for (JsonNode i : interfaces) {
      if (!i.path("ip").asText("").isBlank()) {
        return i;
      }
    }
    return null;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private static Long longValue(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Instant epochSeconds(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isNumber() ? Instant.ofEpochSecond((long) value.asDouble()) : null;
  }
}
