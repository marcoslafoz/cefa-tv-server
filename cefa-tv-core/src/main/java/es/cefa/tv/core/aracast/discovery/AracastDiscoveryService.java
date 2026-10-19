package es.cefa.tv.core.aracast.discovery;

import es.cefa.tv.core.aracast.dto.DiscoveredPlayer;
import es.cefa.tv.core.shared.util.Ipv4Range;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Descubre players Aracast enviando el datagrama {@code A} al puerto UDP de descubrimiento de cada
 * dirección. El player responde con {@code <hostname>\n<mac>\n<modelo> v<versión>}.
 */
@Slf4j
@Service
public class AracastDiscoveryService {

  private static final byte[] PROBE = "A".getBytes(StandardCharsets.US_ASCII);
  private static final int PACING_BATCH = 64;

  private final int port;
  private final int waitMs;
  private final int maxHosts;

  /** Crea el servicio con el puerto, la espera y el máximo de direcciones configurados. */
  public AracastDiscoveryService(
      @Value("${cefa.tv.aracast.discovery.port:30333}") int port,
      @Value("${cefa.tv.aracast.discovery.wait-ms:1500}") int waitMs,
      @Value("${cefa.tv.aracast.discovery.max-hosts:2048}") int maxHosts) {
    this.port = port;
    this.waitMs = waitMs;
    this.maxHosts = maxHosts;
  }

  /**
   * Expande los rangos en una lista de direcciones sin duplicados.
   *
   * @throws IllegalArgumentException si algún rango no es válido o el total supera el máximo
   */
  public List<String> expand(Collection<String> ranges) {
    Set<String> addresses = new LinkedHashSet<>();
    long total = 0;
    for (String r : ranges) {
      Ipv4Range range = Ipv4Range.parse(r);
      total += range.size();
      if (total > maxHosts) {
        throw new IllegalArgumentException(
            "Los rangos suman más de " + maxHosts + " direcciones; divide la sede o redúcelos");
      }
      addresses.addAll(range.addresses());
    }
    return new ArrayList<>(addresses);
  }

  /**
   * Barre las direcciones y devuelve los players que responden, en orden de respuesta. Hace una
   * segunda ronda con las direcciones que no han respondido, porque UDP puede perder paquetes.
   *
   * @param addresses direcciones IPv4 a barrer
   */
  public List<DiscoveredPlayer> scan(List<String> addresses) {
    Map<String, DiscoveredPlayer> found = new LinkedHashMap<>();
    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(100);
      byte[] buffer = new byte[2048];
      sendRound(socket, buffer, found, addresses);
      List<String> pending = addresses.stream().filter(a -> !found.containsKey(a)).toList();
      if (!pending.isEmpty()) {
        sendRound(socket, buffer, found, pending);
      }
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo realizar el barrido UDP", e);
    }
    log.info("Barrido de {} direcciones: {} players", addresses.size(), found.size());
    return new ArrayList<>(found.values());
  }

  private void sendRound(
      DatagramSocket socket,
      byte[] buffer,
      Map<String, DiscoveredPlayer> found,
      List<String> addresses)
      throws IOException {
    for (int i = 0; i < addresses.size(); i++) {
      send(socket, addresses.get(i));
      if (i % PACING_BATCH == PACING_BATCH - 1) {
        drain(socket, buffer, found, System.currentTimeMillis() + 5);
      }
    }
    drain(socket, buffer, found, System.currentTimeMillis() + waitMs);
  }

  private void send(DatagramSocket socket, String address) {
    try {
      socket.send(new DatagramPacket(PROBE, PROBE.length, InetAddress.getByName(address), port));
    } catch (IOException e) {
      log.trace("Sin ruta hacia {}: {}", address, e.getMessage());
    }
  }

  /** Sondea una sola dirección. */
  public Optional<DiscoveredPlayer> probe(String host) {
    return scan(List.of(host)).stream().findFirst();
  }

  private void drain(
      DatagramSocket socket, byte[] buffer, Map<String, DiscoveredPlayer> found, long until)
      throws IOException {
    while (System.currentTimeMillis() < until) {
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      try {
        socket.receive(packet);
      } catch (SocketTimeoutException e) {
        continue;
      } catch (IOException e) {
        log.trace("Error ICMP recibido durante el barrido: {}", e.getMessage());
        continue;
      }
      String host = packet.getAddress().getHostAddress();
      String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
      parse(host, text).ifPresent(p -> found.putIfAbsent(host, p));
    }
  }

  /**
   * Interpreta la respuesta de un player.
   *
   * @param host IP de origen de la respuesta
   * @param text cuerpo del datagrama
   */
  static Optional<DiscoveredPlayer> parse(String host, String text) {
    String[] lines = text.trim().split("\\r?\\n");
    if (lines.length < 3) {
      return Optional.empty();
    }
    String product = lines[2].trim();
    int v = product.lastIndexOf(" v");
    String model = v > 0 ? product.substring(0, v) : product;
    String version = v > 0 ? product.substring(v + 2) : null;
    return Optional.of(
        new DiscoveredPlayer(host, lines[0].trim(), lines[1].trim().toLowerCase(), model, version));
  }
}
