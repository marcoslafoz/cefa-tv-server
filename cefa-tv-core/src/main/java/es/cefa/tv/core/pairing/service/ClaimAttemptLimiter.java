package es.cefa.tv.core.pairing.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Limita por usuario el número de códigos de emparejamiento erróneos por minuto. */
@Component
public class ClaimAttemptLimiter {

  private static final int MAX_FAILURES = 5;
  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final Map<UUID, Deque<Instant>> failures = new ConcurrentHashMap<>();

  /**
   * Indica si el usuario ha agotado sus intentos erróneos en la ventana actual.
   *
   * @param userId usuario
   * @param now instante de evaluación
   * @return {@code true} si debe rechazarse el intento
   */
  public boolean isBlocked(UUID userId, Instant now) {
    Deque<Instant> recent = failures.get(userId);
    if (recent == null) {
      return false;
    }
    synchronized (recent) {
      purge(recent, now);
      return recent.size() >= MAX_FAILURES;
    }
  }

  /**
   * Registra un intento erróneo.
   *
   * @param userId usuario
   * @param now instante del fallo
   */
  public void recordFailure(UUID userId, Instant now) {
    Deque<Instant> recent = failures.computeIfAbsent(userId, id -> new ArrayDeque<>());
    synchronized (recent) {
      purge(recent, now);
      recent.addLast(now);
    }
  }

  /**
   * Olvida los fallos de un usuario tras un emparejamiento correcto.
   *
   * @param userId usuario
   */
  public void reset(UUID userId) {
    failures.remove(userId);
  }

  /** Descarta los fallos fuera de la ventana. */
  private void purge(Deque<Instant> recent, Instant now) {
    Instant limit = now.minus(WINDOW);
    while (!recent.isEmpty() && recent.peekFirst().isBefore(limit)) {
      recent.removeFirst();
    }
  }
}
