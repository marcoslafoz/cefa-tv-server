package es.cefa.tv.core.aracast.management;

import java.util.regex.Pattern;

/** Validaciones de una configuración de red IPv4. */
public final class NetworkValidator {

  private static final Pattern IPV4 =
      Pattern.compile("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");
  private static final Pattern HOSTNAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");

  private NetworkValidator() {}

  /** Indica si el texto es una dirección IPv4. */
  public static boolean isIpv4(String value) {
    return value != null && IPV4.matcher(value.trim()).matches();
  }

  /** Dirección IPv4 como entero sin signo. */
  public static long toLong(String ip) {
    long value = 0;
    for (String octet : ip.trim().split("\\.")) {
      value = (value << 8) | Integer.parseInt(octet);
    }
    return value;
  }

  /** Indica si es una máscara de red válida: unos seguidos de ceros, ni todo unos ni todo ceros. */
  public static boolean isNetmask(String value) {
    if (!isIpv4(value)) {
      return false;
    }
    long mask = toLong(value);
    long inverse = ~mask & 0xFFFFFFFFL;
    return mask != 0 && mask != 0xFFFFFFFFL && ((inverse + 1) & inverse) == 0;
  }

  /** Indica si el nombre de equipo es válido: letras, números, guion y guion bajo. */
  public static boolean isHostname(String value) {
    return value != null && HOSTNAME.matcher(value).matches();
  }

  /**
   * Comprueba la configuración completa.
   *
   * @return el motivo del primer problema encontrado, o {@code null} si es válida
   */
  public static String validate(
      String ip, String netmask, String gateway, String dnsPrimary, String dnsSecondary) {
    if (!isIpv4(ip)) {
      return "La IP no es válida";
    }
    if (!isNetmask(netmask)) {
      return "La máscara no es válida";
    }
    if (!isIpv4(gateway)) {
      return "La puerta de enlace no es válida";
    }
    if (!isIpv4(dnsPrimary)) {
      return "El DNS primario no es válido";
    }
    if (dnsSecondary != null && !dnsSecondary.isBlank() && !isIpv4(dnsSecondary)) {
      return "El DNS secundario no es válido";
    }
    long address = toLong(ip);
    long mask = toLong(netmask);
    long gw = toLong(gateway);
    long network = address & mask;
    long broadcast = network | (~mask & 0xFFFFFFFFL);
    if ((gw & mask) != network) {
      return "La puerta de enlace no está en la misma subred que la IP";
    }
    if (address == network || address == broadcast) {
      return "La IP no puede ser la dirección de red ni la de difusión";
    }
    if (address == gw) {
      return "La IP no puede ser la misma que la puerta de enlace";
    }
    return null;
  }
}
