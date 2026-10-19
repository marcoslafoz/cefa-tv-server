package es.cefa.tv.core.shared.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Ipv4RangeTest {

  @Test
  void cidrExcluyeRedYBroadcast() {
    Ipv4Range range = Ipv4Range.parse("10.21.34.0/23");

    assertThat(range.size()).isEqualTo(510);
    assertThat(range.addresses()).first().isEqualTo("10.21.34.1");
    assertThat(range.addresses()).last().isEqualTo("10.21.35.254");
    assertThat(range.contains("10.21.35.165")).isTrue();
    assertThat(range.contains("10.21.36.1")).isFalse();
  }

  @Test
  void admiteIntervalosEIpSuelta() {
    assertThat(Ipv4Range.parse("10.0.0.10-10.0.0.12").addresses())
        .containsExactly("10.0.0.10", "10.0.0.11", "10.0.0.12");
    assertThat(Ipv4Range.parse(" 10.21.34.33 ").addresses()).containsExactly("10.21.34.33");
    assertThat(Ipv4Range.parse("10.0.0.7/32").addresses()).containsExactly("10.0.0.7");
  }

  @Test
  void rechazaFormatosNoValidos() {
    assertThatThrownBy(() -> Ipv4Range.parse("10.0.0.300"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Ipv4Range.parse("10.0.0.0/33"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Ipv4Range.parse("10.0.0.9-10.0.0.1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Ipv4Range.parse("10.0.0.0/24").contains("no-es-ip")).isFalse();
  }
}
