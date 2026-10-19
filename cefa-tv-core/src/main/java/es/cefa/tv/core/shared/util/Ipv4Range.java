package es.cefa.tv.core.shared.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rango de direcciones IPv4. Admite tres formatos: CIDR ({@code 10.21.34.0/23}), intervalo ({@code
 * 10.21.34.10-10.21.34.50}) y dirección suelta ({@code 10.21.34.33}).
 *
 * @param first primera dirección del rango, como entero sin signo
 * @param last última dirección del rango, como entero sin signo
 */
public record Ipv4Range(long first, long last) {

  private static final Pattern IPV4 =
      Pattern.compile("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");

  /**
   * Interpreta un rango. En CIDR de prefijo menor que 31 se excluyen la dirección de red y la de
   * broadcast.
   *
   * @param text rango en cualquiera de los formatos admitidos
   * @throws IllegalArgumentException si el texto no es un rango válido
   */
  public static Ipv4Range parse(String text) {
    String value = text == null ? "" : text.trim();
    if (value.contains("/")) {
      String[] parts = value.split("/", 2);
      long base = toLong(parts[0].trim());
      int prefix;
      try {
        prefix = Integer.parseInt(parts[1].trim());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Prefijo no válido en " + value);
      }
      if (prefix < 0 || prefix > 32) {
        throw new IllegalArgumentException("Prefijo no válido en " + value);
      }
      long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
      long network = base & mask;
      long broadcast = network | (~mask & 0xFFFFFFFFL);
      return prefix >= 31
          ? new Ipv4Range(network, broadcast)
          : new Ipv4Range(network + 1, broadcast - 1);
    }
    if (value.contains("-")) {
      String[] parts = value.split("-", 2);
      long from = toLong(parts[0].trim());
      long to = toLong(parts[1].trim());
      if (to < from) {
        throw new IllegalArgumentException("El rango " + value + " está al revés");
      }
      return new Ipv4Range(from, to);
    }
    long single = toLong(value);
    return new Ipv4Range(single, single);
  }

  /** Número de direcciones del rango. */
  public long size() {
    return last - first + 1;
  }

  /** Indica si la dirección pertenece al rango. Devuelve {@code false} si no es una IPv4. */
  public boolean contains(String ip) {
    if (ip == null || !IPV4.matcher(ip).matches()) {
      return false;
    }
    long value = toLong(ip);
    return value >= first && value <= last;
  }

  /** Lista todas las direcciones del rango en orden. */
  public List<String> addresses() {
    List<String> result = new ArrayList<>((int) Math.min(size(), Integer.MAX_VALUE));
    for (long v = first; v <= last; v++) {
      result.add(toText(v));
    }
    return result;
  }

  private static long toLong(String ip) {
    if (!IPV4.matcher(ip).matches()) {
      throw new IllegalArgumentException("Dirección IPv4 no válida: " + ip);
    }
    long value = 0;
    for (String octet : ip.split("\\.")) {
      value = (value << 8) | Integer.parseInt(octet);
    }
    return value;
  }

  private static String toText(long value) {
    return ((value >> 24) & 0xFF)
        + "."
        + ((value >> 16) & 0xFF)
        + "."
        + ((value >> 8) & 0xFF)
        + "."
        + (value & 0xFF);
  }
}
