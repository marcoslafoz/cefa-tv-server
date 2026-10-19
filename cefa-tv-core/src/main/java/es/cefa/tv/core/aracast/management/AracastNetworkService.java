package es.cefa.tv.core.aracast.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.client.AracastFtpClient;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.exception.PlayerOfflineException;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lectura y cambio de la red (IP estática, máscara, puerta de enlace, DNS) y del nombre de equipo
 * de un Aracast. El cambio se aplica con un fichero {@code .cfg} subido por FTP; si cambia la
 * dirección el player se reinicia y CEFA TV actualiza la IP con la que lo gestiona.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AracastNetworkService {

  static final String CONFIG_FILE = "ctv-network.cfg";

  private static final String INTERFACE = "eth0";

  private final AracastCommandClient commands;
  private final AracastFtpClient ftp;
  private final AracastLocks locks;
  private final AracastPlayerRepository aracasts;
  private final PlayerRepository players;
  private final ObjectMapper mapper;
  private final TransactionTemplate transaction;

  /**
   * Red del player: lo guardado y lo activo ahora.
   *
   * @param host IP o nombre del player
   * @return las dos configuraciones
   */
  public NetworkInfo get(String host) {
    JsonNode result = commands.send(host, "System.getConfiguration").path("result");
    JsonNode eth = result.path("networks").path(INTERFACE);
    JsonNode dns = eth.path("dns");
    String hostname = text(result, "hostname");
    NetworkConfig configured =
        new NetworkConfig(
            hostname,
            text(eth, "type"),
            text(eth, "ip"),
            text(eth, "netmask"),
            text(eth, "gateway"),
            text(dns, "primary"),
            text(dns, "secondary"));
    JsonNode live = firstInterfaceWithIp(result.path("real_networks").path("if"));
    JsonNode liveDns = result.path("real_networks").path("dns");
    NetworkConfig effective =
        live == null
            ? configured
            : new NetworkConfig(
                hostname,
                configured.type(),
                text(live, "ip"),
                text(live, "netmask"),
                text(live, "gateway"),
                liveDns.size() > 0 ? liveDns.get(0).asText() : null,
                liveDns.size() > 1 ? liveDns.get(1).asText() : null);
    return new NetworkInfo(configured, effective);
  }

  /**
   * Aplica un cambio de red y nombre. Se calcula contra lo activo para decidir si hace falta
   * reiniciar y contra lo guardado para saber si hay algo que escribir.
   *
   * @param host IP o nombre actual del player
   * @param request configuración completa nueva
   * @return qué se hizo y con qué dirección se llegará al player
   */
  public NetworkChangeResult apply(String host, NetworkChangeRequest request) {
    NetworkInfo info = get(host);
    NetworkConfig saved = info.configured();
    NetworkConfig live = info.effective();
    String ip = request.ip().trim();
    String mask = request.netmask().trim();
    String gateway = request.gateway().trim();
    String dns1 = request.dnsPrimary().trim();
    String dns2 = blankToNull(request.dnsSecondary());
    String hostname = request.hostname().trim();

    String problem = NetworkValidator.validate(ip, mask, gateway, dns1, dns2);
    if (problem != null) {
      throw ApiException.badRequest("INVALID_NETWORK", problem);
    }
    if (!hostname.equals(saved.hostname()) && !NetworkValidator.isHostname(hostname)) {
      throw ApiException.badRequest(
          "INVALID_NETWORK",
          "El nombre de equipo solo puede tener letras, números, guion y guion bajo");
    }
    boolean addressChanged =
        !ip.equals(live.ip()) || !mask.equals(live.netmask()) || !gateway.equals(live.gateway());
    boolean savedChanged =
        !ip.equals(saved.ip())
            || !mask.equals(saved.netmask())
            || !gateway.equals(saved.gateway())
            || !hostname.equals(saved.hostname())
            || !dns1.equals(saved.dnsPrimary())
            || !Objects.equals(dns2, blankToNull(saved.dnsSecondary()));
    if (!addressChanged && !savedChanged) {
      throw ApiException.badRequest("INVALID_NETWORK", "No hay ningún cambio que aplicar");
    }
    if (!ip.equals(live.ip())) {
      ensureIpFree(ip);
    }

    ObjectNode result = mapper.createObjectNode();
    result.put("hostname", hostname);
    ObjectNode eth = result.putObject("networks").putObject(INTERFACE);
    eth.put("type", "static");
    eth.put("ip", ip);
    eth.put("netmask", mask);
    eth.put("gateway", gateway);
    ObjectNode dns = eth.putObject("dns");
    dns.put("type", "static");
    dns.put("primary", dns1);
    dns.put("secondary", dns2 == null ? "" : dns2);

    locks.withLock(
        host,
        () -> {
          ftp.upload(host, CONFIG_FILE, new ByteArrayInputStream(configJson(result)));
          commands.send(host, "System.setConfiguration", Map.of("file", CONFIG_FILE));
          return null;
        });

    boolean reboot = request.reboot() || addressChanged;
    if (reboot) {
      reboot(host);
    }
    String newHost = updateInventory(host, hostname, addressChanged ? ip : null, saved);
    log.info("Red de {} cambiada (dirección: {}, reinicio: {})", host, addressChanged, reboot);
    return new NetworkChangeResult(
        newHost, addressChanged, reboot, message(addressChanged, reboot));
  }

  private void ensureIpFree(String ip) {
    if (aracasts.findByHostIgnoreCase(ip).isPresent()) {
      throw ApiException.conflict(
          "IP_IN_USE", "Ya hay un player Aracast dado de alta con la IP " + ip);
    }
    if (commands.isReachable(ip, 1500)) {
      throw ApiException.conflict("IP_IN_USE", "Ya hay un dispositivo respondiendo en la IP " + ip);
    }
  }

  private void reboot(String host) {
    try {
      commands.send(host, "System.reboot");
    } catch (PlayerOfflineException e) {
      throw e;
    } catch (AracastException e) {
      log.debug("El player {} cerró la conexión al reiniciar: {}", host, e.getMessage());
    }
  }

  private String updateInventory(
      String host, String hostname, String newIp, NetworkConfig current) {
    String target = newIp != null ? newIp : host;
    if (newIp == null && hostname.equals(current.hostname())) {
      return target;
    }
    transaction.executeWithoutResult(
        status -> {
          AracastPlayer aracast = aracasts.findByHostIgnoreCase(host).orElse(null);
          if (aracast == null) {
            return;
          }
          if (newIp != null) {
            aracast.setHost(newIp);
            aracast.setPublishedHash(null);
          }
          players
              .findById(aracast.getPlayerId())
              .ifPresent(
                  (Player player) -> {
                    if (newIp != null) {
                      player.setIp(newIp);
                    }
                    player.setHostname(hostname);
                  });
        });
    return target;
  }

  private static String message(boolean addressChanged, boolean reboot) {
    if (addressChanged) {
      return "Configuración enviada. El player se está reiniciando y volverá con la IP nueva en"
          + " un par de minutos.";
    }
    return reboot
        ? "Configuración enviada. El player se está reiniciando para aplicarla."
        : "Configuración guardada. Se aplicará la próxima vez que se reinicie el player.";
  }

  private static JsonNode firstInterfaceWithIp(JsonNode interfaces) {
    for (JsonNode i : interfaces) {
      if (!i.path("ip").asText("").isBlank()) {
        return i;
      }
    }
    return null;
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

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
