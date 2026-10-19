package es.cefa.tv.core.aracast.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.cefa.tv.core.aracast.config.AracastProperties;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.shared.config.CoreProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AracastCredentialsTest {

  private AracastPlayerRepository aracasts;
  private SecretCipher cipher;
  private AracastCredentials credentials;

  @BeforeEach
  void setUp() {
    aracasts = mock(AracastPlayerRepository.class);
    CoreProperties core = mock(CoreProperties.class);
    when(core.jwt()).thenReturn(new CoreProperties.Jwt("a".repeat(40), 12, false));
    cipher = new SecretCipher(core, "");
    AracastProperties properties =
        new AracastProperties(
            5401, 1000, 5481, "user", "ftp-global", 1000, "root", List.of("g1", " g2 ", ""), 1000);
    credentials = new AracastCredentials(aracasts, properties, cipher);
  }

  @Test
  void usesThePlayersOwnSshPasswordFirstAndTheGlobalOnesAfter() {
    AracastPlayer player = new AracastPlayer();
    credentials.store(player, "propia", null);
    when(aracasts.findByHostIgnoreCase("10.0.0.5")).thenReturn(Optional.of(player));

    assertThat(credentials.sshPasswords("10.0.0.5")).containsExactly("propia", "g1", "g2");
  }

  @Test
  void fallsBackToTheGlobalPasswordsWhenThePlayerHasNone() {
    when(aracasts.findByHostIgnoreCase("10.0.0.5")).thenReturn(Optional.empty());

    assertThat(credentials.sshPasswords("10.0.0.5")).containsExactly("g1", "g2");
    assertThat(credentials.ftpPassword("10.0.0.5")).isEqualTo("ftp-global");
  }

  @Test
  void storesThePasswordsEncryptedAndClearsThemWithAnEmptyValue() {
    AracastPlayer player = new AracastPlayer();

    credentials.store(player, "ssh", "ftp");
    assertThat(player.getSshPasswordEnc()).isNotEqualTo("ssh");
    assertThat(cipher.decrypt(player.getFtpPasswordEnc())).isEqualTo("ftp");

    credentials.store(player, null, "");
    assertThat(player.getSshPasswordEnc()).isNotNull();
    assertThat(player.getFtpPasswordEnc()).isNull();

    credentials.store(player, "", null);
    assertThat(player.getSshPasswordEnc()).isNull();
  }

  @Test
  void ignoresAStoredPasswordThatCannotBeDecrypted() {
    AracastPlayer player = new AracastPlayer();
    player.setSshPasswordEnc("no-es-base64-valido-!!");
    when(aracasts.findByHostIgnoreCase("10.0.0.5")).thenReturn(Optional.of(player));

    assertThat(credentials.sshPasswords("10.0.0.5")).containsExactly("g1", "g2");
  }
}
