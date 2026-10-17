package es.cefa.tv.core.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.pairing.service.PairingCodes;
import org.junit.jupiter.api.Test;

/** Pruebas de generación y normalización de códigos de emparejamiento. */
class PairingCodesTest {

  @Test
  void generatedCodesHaveTheExpectedShapeWithoutAmbiguousCharacters() {
    for (int i = 0; i < 200; i++) {
      assertThat(PairingCodes.generate()).matches("[A-HJKMNP-Z2-9]{3}-[A-HJKMNP-Z2-9]{3}");
    }
  }

  @Test
  void normalizeAcceptsLowercaseSpacesAndMissingDash() {
    assertThat(PairingCodes.normalize(" k7q 92x ")).isEqualTo("K7Q-92X");
    assertThat(PairingCodes.normalize("K7Q92X")).isEqualTo("K7Q-92X");
    assertThat(PairingCodes.normalize("k7q-92x")).isEqualTo("K7Q-92X");
  }

  @Test
  void normalizeKeepsTextWithTheWrongLengthWithoutDash() {
    assertThat(PairingCodes.normalize("ab-c")).isEqualTo("ABC");
  }
}
