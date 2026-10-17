package es.cefa.tv.core.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.schedule.model.ScheduleRule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas de los límites de la franja horaria de una regla. */
class ScheduleRuleTest {

  @Test
  void endTimeIsExclusive() {
    ScheduleRule rule =
        new ScheduleRule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            null,
            null,
            LocalTime.of(9, 0),
            LocalTime.of(10, 0),
            Set.of(1),
            false,
            true);
    LocalDate monday = LocalDate.parse("2026-10-19");

    assertThat(rule.isActiveAt(monday, LocalTime.of(9, 0))).isTrue();
    assertThat(rule.isActiveAt(monday, LocalTime.of(9, 59))).isTrue();
    assertThat(rule.isActiveAt(monday, LocalTime.of(10, 0))).isFalse();
    assertThat(rule.isActiveAt(monday.plusDays(1), LocalTime.of(9, 30))).isFalse();
  }
}
