package es.cefa.tv.core.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.pairing.service.ClaimAttemptLimiter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas del límite de códigos erróneos por usuario y minuto. */
class ClaimAttemptLimiterTest {

  private final ClaimAttemptLimiter limiter = new ClaimAttemptLimiter();
  private final UUID user = UUID.randomUUID();
  private final Instant start = Instant.parse("2026-10-16T10:00:00Z");

  @Test
  void blocksAfterFiveFailuresInsideTheWindow() {
    for (int i = 0; i < 4; i++) {
      limiter.recordFailure(user, start.plusSeconds(i));
    }
    assertThat(limiter.isBlocked(user, start.plusSeconds(10))).isFalse();

    limiter.recordFailure(user, start.plusSeconds(5));

    assertThat(limiter.isBlocked(user, start.plusSeconds(10))).isTrue();
  }

  @Test
  void unblocksOnceTheFailuresLeaveTheWindow() {
    for (int i = 0; i < 5; i++) {
      limiter.recordFailure(user, start);
    }

    assertThat(limiter.isBlocked(user, start.plusSeconds(30))).isTrue();
    assertThat(limiter.isBlocked(user, start.plusSeconds(61))).isFalse();
  }

  @Test
  void resetForgetsPreviousFailures() {
    for (int i = 0; i < 5; i++) {
      limiter.recordFailure(user, start);
    }

    limiter.reset(user);

    assertThat(limiter.isBlocked(user, start)).isFalse();
  }

  @Test
  void usersAreLimitedIndependently() {
    for (int i = 0; i < 5; i++) {
      limiter.recordFailure(user, start);
    }

    assertThat(limiter.isBlocked(UUID.randomUUID(), start)).isFalse();
  }
}
