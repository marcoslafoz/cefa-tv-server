package es.cefa.tv.core.aracast.security;

import es.cefa.tv.core.aracast.config.AracastProperties;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resuelve las contraseñas con las que hablar con un player: la propia del reproductor, si se
 * configuró, y las globales del {@code .env} como respaldo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AracastCredentials {

  private final AracastPlayerRepository aracasts;
  private final AracastProperties properties;
  private final SecretCipher cipher;

  /**
   * Contraseñas SSH a probar en un player, por orden: la suya y después las globales.
   *
   * @param host IP o nombre del player
   * @return las contraseñas, sin repetidas
   */
  public List<String> sshPasswords(String host) {
    List<String> passwords = new ArrayList<>();
    own(host, AracastPlayer::getSshPasswordEnc).ifPresent(passwords::add);
    properties.cleanSshPasswords().stream()
        .filter(p -> !passwords.contains(p))
        .forEach(passwords::add);
    return passwords;
  }

  /**
   * Contraseña del FTP de un player: la suya o, si no tiene, la global.
   *
   * @param host IP o nombre del player
   * @return la contraseña, o vacío si no hay ninguna
   */
  public String ftpPassword(String host) {
    return own(host, AracastPlayer::getFtpPasswordEnc)
        .orElse(properties.ftpPassword() == null ? "" : properties.ftpPassword());
  }

  /**
   * Guarda cifradas las contraseñas de un player. Un valor nulo deja la actual y uno vacío la borra
   * para volver a las globales.
   *
   * @param aracast datos del player
   * @param sshPassword contraseña SSH nueva
   * @param ftpPassword contraseña FTP nueva
   */
  public void store(AracastPlayer aracast, String sshPassword, String ftpPassword) {
    if (sshPassword != null) {
      aracast.setSshPasswordEnc(sshPassword.isEmpty() ? null : cipher.encrypt(sshPassword));
    }
    if (ftpPassword != null) {
      aracast.setFtpPasswordEnc(ftpPassword.isEmpty() ? null : cipher.encrypt(ftpPassword));
    }
  }

  private java.util.Optional<String> own(
      String host, java.util.function.Function<AracastPlayer, String> field) {
    return aracasts
        .findByHostIgnoreCase(host)
        .map(field)
        .filter(value -> value != null && !value.isBlank())
        .map(
            encrypted -> {
              try {
                return cipher.decrypt(encrypted);
              } catch (IllegalStateException e) {
                log.warn("La contraseña guardada de {} no se puede descifrar", host);
                return null;
              }
            });
  }
}
