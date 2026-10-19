package es.cefa.tv.core.aracast.management;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Exclusión mutua por player: la publicación de la programación, el cambio de hora o de red y el
 * resto de operaciones que usan el FTP y el fichero {@code lock} del player no se pisan entre sí.
 */
@Component
public class AracastLocks {

  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  /**
   * Ejecuta una acción con el player bloqueado; es reentrante para la misma hebra.
   *
   * @param host IP o nombre del player
   * @param action acción a ejecutar
   * @param <T> tipo del resultado
   * @return lo que devuelve la acción
   */
  public <T> T withLock(String host, Supplier<T> action) {
    ReentrantLock lock = locks.computeIfAbsent(host.toLowerCase(), key -> new ReentrantLock());
    lock.lock();
    try {
      return action.get();
    } finally {
      lock.unlock();
    }
  }
}
