package es.cefa.tv.core.publicinfo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Información pública del servidor, usada por los reproductores para validar el descubrimiento. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicInfoController {

  private final String version;

  /**
   * Crea el controlador.
   *
   * @param version versión de la aplicación
   */
  public PublicInfoController(@Value("${info.app.version:0.1.0}") String version) {
    this.version = version;
  }

  /**
   * Información del servicio.
   *
   * @return nombre y versión del servidor
   */
  @GetMapping("/info")
  public Info info() {
    return new Info("cefa-tv-server", "CEFA TV", version);
  }

  /**
   * Información pública.
   *
   * @param service identificador fijo del servicio
   * @param name nombre legible
   * @param version versión
   */
  public record Info(String service, String name, String version) {}
}
