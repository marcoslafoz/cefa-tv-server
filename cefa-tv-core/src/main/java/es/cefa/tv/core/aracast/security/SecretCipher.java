package es.cefa.tv.core.aracast.security;

import es.cefa.tv.core.shared.config.CoreProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra con AES-256-GCM las contraseñas de los players que se guardan en base de datos. La clave
 * sale de {@code ARACAST_SECRET_KEY} o, si no está definida, del secreto de sesión de CEFA TV.
 */
@Component
public class SecretCipher {

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  /**
   * Crea el cifrador.
   *
   * @param properties propiedades de la aplicación, de donde sale el secreto de reserva
   * @param configuredKey valor de {@code ARACAST_SECRET_KEY}, o vacío
   */
  public SecretCipher(
      CoreProperties properties, @Value("${cefa.tv.aracast.secret-key:}") String configuredKey) {
    String material =
        configuredKey == null || configuredKey.isBlank()
            ? "aracast:" + properties.jwt().secret()
            : configuredKey;
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
      this.key = new SecretKeySpec(digest, "AES");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("No se pudo preparar el cifrado", e);
    }
  }

  /**
   * Cifra un texto.
   *
   * @param plain texto en claro
   * @return IV y texto cifrado en Base64
   */
  public String encrypt(String plain) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] joined = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, joined, 0, iv.length);
      System.arraycopy(encrypted, 0, joined, iv.length, encrypted.length);
      return Base64.getEncoder().encodeToString(joined);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("No se pudo cifrar", e);
    }
  }

  /**
   * Descifra un texto cifrado con {@link #encrypt}.
   *
   * @param encrypted IV y texto cifrado en Base64
   * @return el texto en claro
   */
  public String decrypt(String encrypted) {
    try {
      byte[] joined = Base64.getDecoder().decode(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, joined, 0, IV_BYTES));
      byte[] plain = cipher.doFinal(joined, IV_BYTES, joined.length - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("No se pudo descifrar la contraseña guardada", e);
    }
  }
}
