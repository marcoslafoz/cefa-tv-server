package es.cefa.tv.core.aracast.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NetworkValidatorTest {

  @Test
  void aceptaUnaConfiguracionCorrecta() {
    assertThat(
            NetworkValidator.validate(
                "10.21.34.33", "255.255.254.0", "10.21.35.1", "10.21.55.10", "10.21.34.10"))
        .isNull();
    assertThat(
            NetworkValidator.validate(
                "10.21.34.33", "255.255.254.0", "10.21.35.1", "8.8.8.8", null))
        .isNull();
  }

  @Test
  void rechazaMascarasNoContiguas() {
    assertThat(NetworkValidator.isNetmask("255.255.254.0")).isTrue();
    assertThat(NetworkValidator.isNetmask("255.0.255.0")).isFalse();
    assertThat(NetworkValidator.isNetmask("0.0.0.0")).isFalse();
    assertThat(NetworkValidator.isNetmask("255.255.255.255")).isFalse();
    assertThat(NetworkValidator.isNetmask("255.255.255")).isFalse();
  }

  @Test
  void rechazaPuertaDeEnlaceFueraDeLaSubred() {
    assertThat(
            NetworkValidator.validate(
                "10.21.34.33", "255.255.254.0", "10.21.40.1", "8.8.8.8", null))
        .contains("misma subred");
  }

  @Test
  void rechazaDireccionDeRedDifusionOPuertaDeEnlace() {
    assertThat(
            NetworkValidator.validate("10.21.34.0", "255.255.254.0", "10.21.35.1", "8.8.8.8", null))
        .contains("red");
    assertThat(
            NetworkValidator.validate(
                "10.21.35.255", "255.255.254.0", "10.21.35.1", "8.8.8.8", null))
        .contains("difusión");
    assertThat(
            NetworkValidator.validate("10.21.35.1", "255.255.254.0", "10.21.35.1", "8.8.8.8", null))
        .contains("puerta de enlace");
  }

  @Test
  void rechazaDireccionesYNombresNoValidos() {
    assertThat(
            NetworkValidator.validate(
                "10.21.34.300", "255.255.254.0", "10.21.35.1", "8.8.8.8", null))
        .contains("IP");
    assertThat(NetworkValidator.validate("10.21.34.33", "255.255.254.0", "10.21.35.1", "no", null))
        .contains("DNS primario");
    assertThat(NetworkValidator.isHostname("ARAPROD03")).isTrue();
    assertThat(NetworkValidator.isHostname("Hall_oficinas-2")).isTrue();
    assertThat(NetworkValidator.isHostname("con espacio")).isFalse();
    assertThat(NetworkValidator.isHostname("acción")).isFalse();
    assertThat(NetworkValidator.isHostname("")).isFalse();
  }
}
