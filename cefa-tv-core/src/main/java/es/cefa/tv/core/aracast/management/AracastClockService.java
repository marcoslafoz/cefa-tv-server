package es.cefa.tv.core.aracast.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.client.AracastFtpClient;
import es.cefa.tv.core.aracast.client.AracastSshClient;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.exception.PlayerOfflineException;
import java.io.ByteArrayInputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Ajuste de la hora de los Aracast. Hay dos vías: poner la hora del servidor mediante la
 * configuración del propio player (un fichero {@code .cfg} subido por FTP) o sincronizar con un
 * servidor NTP ejecutando {@code ntpdate} por SSH.
 */
@Service
@RequiredArgsConstructor
public class AracastClockService {

  static final String CONFIG_FILE = "ctv-clock.cfg";

  private static final DateTimeFormatter PLAYER_CLOCK =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final AracastCommandClient commands;
  private final AracastFtpClient ftp;
  private final AracastSshClient ssh;
  private final AracastLocks locks;
  private final ObjectMapper mapper;

  @Value("${cefa.tv.aracast.ntp-servers:10.21.34.10,10.21.35.2}")
  private List<String> ntpServers;

  /**
   * Lee la hora del player.
   *
   * @param host IP o nombre del player
   * @return hora, zona y desfase
   */
  public ClockResult read(String host) {
    return read(host, null, null);
  }

  /**
   * Pone en el player la hora actual del servidor, en la zona horaria del player.
   *
   * @param host IP o nombre del player
   * @return la hora resultante
   */
  public ClockResult syncFromServer(String host) {
    ZoneId zone = zone(host);
    String now = LocalDateTime.now(zone).format(PLAYER_CLOCK);
    ObjectNode result = mapper.createObjectNode();
    result.putObject("clock").put("datetime", now);
    String how = "";
    try {
      locks.withLock(
          host,
          () -> {
            ftp.upload(host, CONFIG_FILE, new ByteArrayInputStream(configJson(result)));
            commands.send(host, "System.setConfiguration", Map.of("file", CONFIG_FILE));
            return null;
          });
    } catch (PlayerOfflineException e) {
      throw e;
    } catch (AracastException e) {
      setClockOverSsh(host);
      how = " (por SSH)";
    }
    return read(host, "SERVER", "Hora del servidor aplicada" + how + ": " + now);
  }

  /**
   * Sincroniza con un servidor NTP desde el propio player.
   *
   * @param host IP o nombre del player
   * @param server servidor NTP; vacío para probar por orden los internos configurados
   * @return la hora resultante
   */
  public ClockResult syncNtp(String host, String server) {
    List<String> candidates =
        server == null || server.isBlank() ? cleanServers() : List.of(server.trim());
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException("No hay servidores NTP configurados; indica uno");
    }
    AracastException last = null;
    for (String candidate : candidates) {
      String ip = resolveIpv4(candidate);
      String command =
          "out=$(ntpdate -u -t 2 "
              + ip
              + " 2>&1 || ntpdate -t 2 "
              + ip
              + " 2>&1); rc=$?; [ $rc -eq 0 ] && hwclock --systohc 2>/dev/null; echo \"$out\";"
              + " exit $rc";
      try {
        String output = ssh.execText(host, command, 40);
        return read(host, "NTP", "Servidor " + candidate + ": " + lastLine(output));
      } catch (PlayerOfflineException e) {
        throw e;
      } catch (AracastException e) {
        last = e;
      }
    }
    throw new AracastException(
        "Ningún servidor NTP ha respondido ("
            + String.join(", ", candidates)
            + "): "
            + (last == null ? "" : last.getMessage()),
        last);
  }

  private void setClockOverSsh(String host) {
    Instant now = Instant.now();
    String stamp = now.getEpochSecond() + "." + String.format("%03d", now.getNano() / 1_000_000);
    ssh.execText(
        host,
        "date -u -s @" + stamp + " >/dev/null && { hwclock --systohc 2>/dev/null; true; }",
        20);
  }

  private ClockResult read(String host, String method, String detail) {
    JsonNode clock = commands.send(host, "System.getConfiguration").path("result").path("clock");
    String playerTime = text(clock, "datetime");
    String timezone = text(clock, "timezone");
    return new ClockResult(playerTime, timezone, offset(playerTime, timezone), method, detail);
  }

  private ZoneId zone(String host) {
    JsonNode clock = commands.send(host, "System.getConfiguration").path("result").path("clock");
    try {
      return ZoneId.of(text(clock, "timezone"));
    } catch (DateTimeException | NullPointerException e) {
      return ZoneId.of("Europe/Madrid");
    }
  }

  private List<String> cleanServers() {
    return ntpServers == null
        ? List.of()
        : ntpServers.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  static String resolveIpv4(String server) {
    try {
      for (InetAddress a : InetAddress.getAllByName(server)) {
        if (a instanceof Inet4Address) {
          return a.getHostAddress();
        }
      }
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("No se pudo resolver el servidor " + server);
    }
    throw new IllegalArgumentException("El servidor " + server + " no tiene dirección IPv4");
  }

  static Long offset(String playerTime, String timezone) {
    if (playerTime == null || timezone == null) {
      return null;
    }
    try {
      LocalDateTime player = LocalDateTime.parse(playerTime, PLAYER_CLOCK);
      return Duration.between(LocalDateTime.now(ZoneId.of(timezone)), player).toSeconds();
    } catch (DateTimeException e) {
      return null;
    }
  }

  private byte[] configJson(ObjectNode result) {
    ObjectNode command = mapper.createObjectNode();
    command.put("action", "System.setConfiguration");
    command.set("result", result);
    ObjectNode root = mapper.createObjectNode();
    root.set("command", command);
    return root.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String text(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isMissingNode() || v.isNull() ? null : v.asText();
  }

  private static String lastLine(String output) {
    String[] lines = output.trim().split("\\R");
    return lines.length == 0 ? "" : lines[lines.length - 1];
  }
}
