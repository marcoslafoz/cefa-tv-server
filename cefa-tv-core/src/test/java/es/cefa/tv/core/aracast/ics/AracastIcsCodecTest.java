package es.cefa.tv.core.aracast.ics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AracastIcsCodecTest {

  private final AracastIcsCodec codec =
      new AracastIcsCodec(Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC));

  private static final String DESKTOP_APP_ICS =
      "BEGIN:VCALENDAR\r\n"
          + "PRODID:-//AraCast//iCal4j 1.0//ES\r\n"
          + "VERSION:2.0\r\n"
          + "X-ARACAST-SCHEDULE:Programacion CEFA Oficinas\r\n"
          + "BEGIN:VEVENT\r\n"
          + "DTSTART:20000703T000000\r\n"
          + "DTEND:20000703T235930\r\n"
          + "SUMMARY:CEFA Oficinas\r\n"
          + "RRULE:FREQ=DAILY\r\n"
          + "X-ARACAST-CONTENT:CEFA Oficinas\r\n"
          + "X-ARACAST-TYPE:3\r\n"
          + "X-ARACAST-DURATION:86370000\r\n"
          + "END:VEVENT\r\n"
          + "BEGIN:VEVENT\r\n"
          + "DTSTART:20000703T200000\r\n"
          + "X-ARACAST-TYPE:4\r\n"
          + "END:VEVENT\r\n"
          + "END:VCALENDAR\r\n";

  @Test
  void leeElFicheroDeLaAplicacionDeEscritorio() {
    IcsSchedule schedule = codec.decode(DESKTOP_APP_ICS);

    assertThat(schedule.name()).isEqualTo("Programacion CEFA Oficinas");
    assertThat(schedule.entries())
        .containsExactly(
            new IcsEntry("CEFA Oficinas", LocalTime.MIDNIGHT, LocalTime.of(23, 59, 30), null));
    assertThat(schedule.unsupportedEvents()).isEqualTo(1);
  }

  @Test
  void generaEventosDiariosYSemanalesConDuracionEnMilisegundos() {
    IcsSchedule schedule =
        new IcsSchedule(
            "Prueba",
            List.of(
                new IcsEntry("Carta de ajuste", LocalTime.of(8, 0), LocalTime.of(14, 0), null),
                new IcsEntry(
                    "Celula 4",
                    LocalTime.of(14, 0),
                    LocalTime.of(20, 30),
                    List.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))),
            0);

    String ics = codec.encode(schedule);

    assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
    assertThat(ics).contains("X-ARACAST-SCHEDULE:Prueba\r\n");
    assertThat(ics).contains("DTSTART:20000703T080000\r\n", "DTEND:20000703T140000\r\n");
    assertThat(ics).contains("RRULE:FREQ=DAILY\r\n", "X-ARACAST-DURATION:21600000\r\n");
    assertThat(ics).contains("RRULE:FREQ=WEEKLY;BYDAY=MO,FR\r\n");
    assertThat(ics).contains("DTSTAMP:20261016T100000Z\r\n");
  }

  @Test
  void loGeneradoSeVuelveALeerIgual() {
    IcsSchedule original =
        new IcsSchedule(
            "Ida y vuelta",
            List.of(
                new IcsEntry("A, con coma", LocalTime.of(9, 0), LocalTime.of(10, 0), null),
                new IcsEntry(
                    "B", LocalTime.of(10, 0), LocalTime.of(11, 0), List.of(DayOfWeek.SUNDAY))),
            0);

    IcsSchedule decoded = codec.decode(codec.encode(original));

    assertThat(decoded).isEqualTo(original);
  }
}
