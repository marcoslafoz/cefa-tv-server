package es.cefa.tv.core.pairing.service;

import java.security.SecureRandom;
import java.util.Locale;

/** Genera y normaliza los códigos de emparejamiento con formato {@code ABC-123}. */
public final class PairingCodes {

  private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final int LENGTH = 6;
  private static final int GROUP = 3;
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Evita la instanciación de la clase de utilidades. */
  private PairingCodes() {}

  /**
   * Genera un código aleatorio sin caracteres ambiguos.
   *
   * @return el código, por ejemplo {@code K7Q-92X}
   */
  public static String generate() {
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < LENGTH; i++) {
      if (i == GROUP) {
        code.append('-');
      }
      code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }

  /**
   * Normaliza un código escrito por una persona: mayúsculas, sin espacios y con el guion en su
   * sitio.
   *
   * @param raw texto introducido
   * @return el código normalizado, o el texto limpio si no tiene la longitud esperada
   */
  public static String normalize(String raw) {
    String clean = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    if (clean.length() != LENGTH) {
      return clean;
    }
    return clean.substring(0, GROUP) + "-" + clean.substring(GROUP);
  }
}
