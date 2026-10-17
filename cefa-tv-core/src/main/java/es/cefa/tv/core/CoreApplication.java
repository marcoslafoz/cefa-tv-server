package es.cefa.tv.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Punto de entrada del backend de CEFA TV. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CoreApplication {

  /**
   * Arranca la aplicación.
   *
   * @param args argumentos de línea de comandos
   */
  public static void main(String[] args) {
    SpringApplication.run(CoreApplication.class, args);
  }
}
