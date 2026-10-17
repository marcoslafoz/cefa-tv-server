package es.cefa.tv.core.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.schedule.model.ScheduleRule;
import es.cefa.tv.core.schedule.model.ScheduleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas del cálculo de estado y próxima emisión en distintas zonas horarias. */
class ScheduleTimingTest {

  private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
  private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");
  private static final Set<Integer> EVERY_DAY = Set.of(1, 2, 3, 4, 5, 6, 7);

  private static ScheduleRule slot(
      LocalTime start, LocalTime end, LocalDate startDate, LocalDate endDate, boolean enabled) {
    return new ScheduleRule(
        UUID.randomUUID(),
        UUID.randomUUID(),
        10,
        startDate,
        endDate,
        start,
        end,
        EVERY_DAY,
        false,
        enabled);
  }

  private static ScheduleRule morning() {
    return slot(LocalTime.of(8, 0), LocalTime.of(10, 0), null, null, true);
  }

  private static ScheduleRule allDay() {
    return new ScheduleRule(
        UUID.randomUUID(), UUID.randomUUID(), 10, null, null, null, null, EVERY_DAY, true, true);
  }

  @Test
  void disabledRuleHasNoNextRun() {
    ScheduleTiming.Timing timing =
        ScheduleTiming.evaluate(
            slot(LocalTime.of(8, 0), LocalTime.of(10, 0), null, null, false),
            List.of(MADRID),
            Instant.parse("2026-10-16T07:30:00Z"));

    assertThat(timing.status()).isEqualTo(ScheduleStatus.DISABLED);
    assertThat(timing.nextRunAt()).isNull();
  }

  @Test
  void sameInstantIsActiveOrNotDependingOnTheZone() {
    Instant now = Instant.parse("2026-10-16T07:30:00Z");

    ScheduleTiming.Timing madrid = ScheduleTiming.evaluate(morning(), List.of(MADRID), now);
    ScheduleTiming.Timing warsaw = ScheduleTiming.evaluate(morning(), List.of(WARSAW), now);
    ScheduleTiming.Timing utc = ScheduleTiming.evaluate(morning(), List.of(UTC), now);
    ScheduleTiming.Timing lisbon = ScheduleTiming.evaluate(morning(), List.of(LISBON), now);

    assertThat(madrid.status()).isEqualTo(ScheduleStatus.ACTIVE_NOW);
    assertThat(warsaw.status()).isEqualTo(ScheduleStatus.ACTIVE_NOW);
    assertThat(utc.status()).isEqualTo(ScheduleStatus.SCHEDULED);
    assertThat(lisbon.status()).isEqualTo(ScheduleStatus.ACTIVE_NOW);
    assertThat(utc.nextRunAt()).isEqualTo(Instant.parse("2026-10-16T08:00:00Z"));
    assertThat(madrid.nextRunAt()).isEqualTo(Instant.parse("2026-10-17T06:00:00Z"));
    assertThat(warsaw.nextRunAt()).isEqualTo(madrid.nextRunAt());
    assertThat(lisbon.nextRunAt()).isEqualTo(Instant.parse("2026-10-17T07:00:00Z"));
  }

  @Test
  void severalZonesAreActiveIfAnyIsAndTakeTheEarliestNextRun() {
    Instant now = Instant.parse("2026-10-16T07:30:00Z");

    ScheduleTiming.Timing timing = ScheduleTiming.evaluate(morning(), List.of(MADRID, UTC), now);

    assertThat(timing.status()).isEqualTo(ScheduleStatus.ACTIVE_NOW);
    assertThat(timing.nextRunAt()).isEqualTo(Instant.parse("2026-10-16T08:00:00Z"));
  }

  @Test
  void noZonesFallsBackToUtc() {
    ScheduleTiming.Timing timing =
        ScheduleTiming.evaluate(morning(), List.of(), Instant.parse("2026-10-16T07:30:00Z"));

    assertThat(timing.status()).isEqualTo(ScheduleStatus.SCHEDULED);
    assertThat(timing.nextRunAt()).isEqualTo(Instant.parse("2026-10-16T08:00:00Z"));
  }

  @Test
  void nextRunFollowsTheSpringForwardChange() {
    Instant now = Instant.parse("2026-03-28T12:00:00Z");

    ScheduleTiming.Timing timing = ScheduleTiming.evaluate(morning(), List.of(MADRID), now);

    assertThat(timing.status()).isEqualTo(ScheduleStatus.SCHEDULED);
    assertThat(timing.nextRunAt()).isEqualTo(Instant.parse("2026-03-29T06:00:00Z"));
  }

  @Test
  void nextRunFollowsTheFallBackChange() {
    Instant now = Instant.parse("2026-10-24T12:00:00Z");

    ScheduleTiming.Timing timing = ScheduleTiming.evaluate(morning(), List.of(MADRID), now);

    assertThat(timing.nextRunAt()).isEqualTo(Instant.parse("2026-10-25T07:00:00Z"));
  }

  @Test
  void lateEveningSlotEndsExactlyAtItsEndTime() {
    ScheduleRule evening = slot(LocalTime.of(22, 0), LocalTime.of(23, 30), null, null, true);

    ScheduleTiming.Timing inside =
        ScheduleTiming.evaluate(evening, List.of(MADRID), Instant.parse("2026-10-16T21:29:00Z"));
    ScheduleTiming.Timing atEnd =
        ScheduleTiming.evaluate(evening, List.of(MADRID), Instant.parse("2026-10-16T21:30:00Z"));

    assertThat(inside.status()).isEqualTo(ScheduleStatus.ACTIVE_NOW);
    assertThat(inside.nextRunAt()).isEqualTo(Instant.parse("2026-10-17T20:00:00Z"));
    assertThat(atEnd.status()).isEqualTo(ScheduleStatus.SCHEDULED);
  }

  @Test
  void endedWhenTheEndDatePassedInItsZone() {
    ScheduleRule bounded =
        slot(LocalTime.of(8, 0), LocalTime.of(10, 0), null, LocalDate.of(2026, 10, 15), true);

    ScheduleTiming.Timing timing =
        ScheduleTiming.evaluate(bounded, List.of(MADRID), Instant.parse("2026-10-16T10:00:00Z"));

    assertThat(timing.status()).isEqualTo(ScheduleStatus.ENDED);
    assertThat(timing.nextRunAt()).isNull();
  }

  @Test
  void endedOnTheLastDayOnceTheSlotIsOver() {
    ScheduleRule bounded =
        slot(LocalTime.of(8, 0), LocalTime.of(10, 0), null, LocalDate.of(2026, 10, 16), true);

    ScheduleTiming.Timing during =
        ScheduleTiming.evaluate(bounded, List.of(MADRID), Instant.parse("2026-10-16T07:00:00Z"));
    ScheduleTiming.Timing after =
        ScheduleTiming.evaluate(bounded, List.of(MADRID), Instant.parse("2026-10-16T09:00:00Z"));

    assertThat(during.status()).isEqualTo(ScheduleStatus.ACTIVE_NOW);
    assertThat(during.nextRunAt()).isNull();
    assertThat(after.status()).isEqualTo(ScheduleStatus.ENDED);
  }

  @Test
  void lastDayCanBeOverInOneZoneAndPendingInAnother() {
    ScheduleRule bounded =
        slot(LocalTime.of(8, 0), LocalTime.of(10, 0), null, LocalDate.of(2026, 10, 16), true);
    Instant now = Instant.parse("2026-10-16T05:00:00Z");
    ZoneId auckland = ZoneId.of("Pacific/Auckland");

    ScheduleTiming.Timing aucklandOnly = ScheduleTiming.evaluate(bounded, List.of(auckland), now);
    ScheduleTiming.Timing both = ScheduleTiming.evaluate(bounded, List.of(auckland, MADRID), now);

    assertThat(aucklandOnly.status()).isEqualTo(ScheduleStatus.ENDED);
    assertThat(aucklandOnly.nextRunAt()).isNull();
    assertThat(both.status()).isEqualTo(ScheduleStatus.SCHEDULED);
    assertThat(both.nextRunAt()).isEqualTo(Instant.parse("2026-10-16T06:00:00Z"));
  }

  @Test
  void futureStartDateIsScheduledWithItsFirstSlot() {
    ScheduleRule future =
        slot(LocalTime.of(8, 0), LocalTime.of(10, 0), LocalDate.of(2026, 12, 1), null, true);

    ScheduleTiming.Timing timing =
        ScheduleTiming.evaluate(future, List.of(MADRID), Instant.parse("2026-10-16T07:00:00Z"));

    assertThat(timing.status()).isEqualTo(ScheduleStatus.SCHEDULED);
    assertThat(timing.nextRunAt()).isEqualTo(Instant.parse("2026-12-01T07:00:00Z"));
  }

  @Test
  void allDayRuleIsActiveAndNextRunIsTheNextLocalMidnight() {
    ScheduleTiming.Timing timing =
        ScheduleTiming.evaluate(allDay(), List.of(MADRID), Instant.parse("2026-10-16T10:00:00Z"));

    assertThat(timing.status()).isEqualTo(ScheduleStatus.ACTIVE_NOW);
    assertThat(timing.nextRunAt()).isEqualTo(Instant.parse("2026-10-16T22:00:00Z"));
  }

  @Test
  void onlyConfiguredWeekdaysCount() {
    ScheduleRule weekdays =
        new ScheduleRule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            10,
            null,
            null,
            LocalTime.of(8, 0),
            LocalTime.of(10, 0),
            Set.of(1, 2, 3, 4, 5),
            false,
            true);

    ScheduleTiming.Timing timing =
        ScheduleTiming.evaluate(weekdays, List.of(MADRID), Instant.parse("2026-10-16T09:00:00Z"));

    assertThat(timing.status()).isEqualTo(ScheduleStatus.SCHEDULED);
    assertThat(timing.nextRunAt()).isEqualTo(Instant.parse("2026-10-19T06:00:00Z"));
  }

  @Test
  void occurrenceStatusFollowsTheWindowOfThatDay() {
    LocalDate day = LocalDate.of(2026, 10, 16);
    LocalTime start = LocalTime.of(8, 0);
    LocalTime end = LocalTime.of(10, 0);

    assertThat(
            ScheduleTiming.occurrenceStatus(
                day, start, end, MADRID, Instant.parse("2026-10-16T05:00:00Z")))
        .isEqualTo(ScheduleStatus.SCHEDULED);
    assertThat(
            ScheduleTiming.occurrenceStatus(
                day, start, end, MADRID, Instant.parse("2026-10-16T07:00:00Z")))
        .isEqualTo(ScheduleStatus.ACTIVE_NOW);
    assertThat(
            ScheduleTiming.occurrenceStatus(
                day, start, end, MADRID, Instant.parse("2026-10-16T08:00:00Z")))
        .isEqualTo(ScheduleStatus.ENDED);
    assertThat(
            ScheduleTiming.occurrenceStatus(
                day, start, end, UTC, Instant.parse("2026-10-16T08:00:00Z")))
        .isEqualTo(ScheduleStatus.ACTIVE_NOW);
  }

  @Test
  void allDayOccurrenceLastsUntilLocalMidnight() {
    LocalDate day = LocalDate.of(2026, 10, 16);

    assertThat(
            ScheduleTiming.occurrenceStatus(
                day, null, null, MADRID, Instant.parse("2026-10-16T21:59:00Z")))
        .isEqualTo(ScheduleStatus.ACTIVE_NOW);
    assertThat(
            ScheduleTiming.occurrenceStatus(
                day, null, null, MADRID, Instant.parse("2026-10-16T22:00:00Z")))
        .isEqualTo(ScheduleStatus.ENDED);
  }
}
