package es.cefa.tv.core.aracast.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.cefa.tv.core.shared.config.CoreProperties;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  private static SecretCipher cipher(String jwtSecret, String configuredKey) {
    CoreProperties properties = mock(CoreProperties.class);
    when(properties.jwt()).thenReturn(new CoreProperties.Jwt(jwtSecret, 12, false));
    return new SecretCipher(properties, configuredKey);
  }

  @Test
  void decryptsWhatItEncrypts() {
    SecretCipher cipher = cipher("a".repeat(40), "");

    String encrypted = cipher.encrypt("o3kmjump");

    assertThat(encrypted).doesNotContain("o3kmjump");
    assertThat(cipher.decrypt(encrypted)).isEqualTo("o3kmjump");
  }

  @Test
  void usesADifferentIvEachTime() {
    SecretCipher cipher = cipher("a".repeat(40), "");

    assertThat(cipher.encrypt("x")).isNotEqualTo(cipher.encrypt("x"));
  }

  @Test
  void cannotDecryptWithAnotherKey() {
    String encrypted = cipher("a".repeat(40), "").encrypt("secreto");

    assertThatThrownBy(() -> cipher("b".repeat(40), "").decrypt(encrypted))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aConfiguredKeyTakesPrecedenceOverTheSessionSecret() {
    String encrypted = cipher("a".repeat(40), "clave-propia").encrypt("secreto");

    assertThat(cipher("otro".repeat(10), "clave-propia").decrypt(encrypted)).isEqualTo("secreto");
  }
}
