package es.cefa.tv.core.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configura el reloj del sistema como bean para poder sustituirlo en las pruebas. */
@Configuration
public class ClockConfig {

  /**
   * Reloj del sistema en UTC.
   *
   * @return el reloj
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
