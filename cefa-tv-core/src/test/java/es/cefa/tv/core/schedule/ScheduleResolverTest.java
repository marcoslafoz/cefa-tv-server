package es.cefa.tv.core.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.schedule.model.ScheduleRule;
import es.cefa.tv.core.schedule.service.ScheduleResolver;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas de la regla que decide qué programación gana en un instante. */
class ScheduleResolverTest {

  private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
  private static final Set<Integer> ALL_DAYS = Set.of(1, 2, 3, 4, 5, 6, 7);

  private ScheduleRule rule(int priority, LocalTime start, LocalTime end, Set<Integer> days) {
    return new ScheduleRule(
        UUID.randomUUID(),
        UUID.randomUUID(),
        priority,
        null,
        null,
        start,
        end,
        days,
        start == null,
        true);
  }

  private ZonedDateTime at(String date, String time) {
    return ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), MADRID);
  }

  @Test
  void higherPriorityWins() {
    ScheduleRule base = rule(0, null, null, ALL_DAYS);
    ScheduleRule promo = rule(10, LocalTime.of(9, 0), LocalTime.of(12, 0), ALL_DAYS);

    assertThat(ScheduleResolver.resolve(List.of(base, promo), at("2026-10-19", "10:00")))
        .contains(promo);
    assertThat(ScheduleResolver.resolve(List.of(base, promo), at("2026-10-19", "12:00")))
        .contains(base);
  }

  @Test
  void tieGoesToTheOneStartingLater() {
    ScheduleRule early = rule(5, LocalTime.of(8, 0), LocalTime.of(18, 0), ALL_DAYS);
    ScheduleRule late = rule(5, LocalTime.of(10, 0), LocalTime.of(14, 0), ALL_DAYS);

    assertThat(ScheduleResolver.resolve(List.of(early, late), at("2026-10-19", "11:00")))
        .contains(late);
    assertThat(ScheduleResolver.resolve(List.of(early, late), at("2026-10-19", "15:00")))
        .contains(early);
  }

  @Test
  void respectsDaysOfWeekAndDateRange() {
    ScheduleRule weekdays =
        new ScheduleRule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            LocalDate.parse("2026-10-20"),
            LocalDate.parse("2026-10-30"),
            null,
            null,
            Set.of(1, 2, 3, 4, 5),
            true,
            true);

    assertThat(ScheduleResolver.resolve(List.of(weekdays), at("2026-10-21", "10:00")))
        .contains(weekdays);
    assertThat(ScheduleResolver.resolve(List.of(weekdays), at("2026-10-24", "10:00"))).isEmpty();
    assertThat(ScheduleResolver.resolve(List.of(weekdays), at("2026-10-19", "10:00"))).isEmpty();
    assertThat(ScheduleResolver.resolve(List.of(weekdays), at("2026-11-02", "10:00"))).isEmpty();
  }

  @Test
  void disabledRulesAreIgnored() {
    ScheduleRule disabled =
        new ScheduleRule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            99,
            null,
            null,
            null,
            null,
            ALL_DAYS,
            true,
            false);

    assertThat(ScheduleResolver.resolve(List.of(disabled), at("2026-10-19", "10:00"))).isEmpty();
  }
}
