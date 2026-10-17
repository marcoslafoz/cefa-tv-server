package es.cefa.tv.core.shared.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Utilidades de resumen criptográfico. */
public final class Hashes {

  /** Evita la instanciación de la clase de utilidades. */
  private Hashes() {}

  /**
   * Crea un resumen SHA-256.
   *
   * @return un {@link MessageDigest} SHA-256 nuevo
   */
  public static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Calcula el SHA-256 de un texto en hexadecimal.
   *
   * @param value texto de entrada
   * @return el resumen en hexadecimal minúscula
   */
  public static String sha256Hex(String value) {
    return HexFormat.of()
        .formatHex(sha256().digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  /**
   * Convierte un resumen a hexadecimal.
   *
   * @param digest resumen ya finalizado
   * @return el hexadecimal en minúscula
   */
  public static String hex(MessageDigest digest) {
    return HexFormat.of().formatHex(digest.digest());
  }
}
