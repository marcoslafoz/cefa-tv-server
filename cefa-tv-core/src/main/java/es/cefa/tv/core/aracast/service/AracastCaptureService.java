package es.cefa.tv.core.aracast.service;

import es.cefa.tv.core.aracast.client.AracastSshClient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Captura lo que muestra la pantalla de un Aracast. El comando de captura del propio player
 * devuelve imágenes vacías, así que se usa {@code ffmpeg} por SSH sobre la pantalla {@code :0}.
 */
@Service
@RequiredArgsConstructor
public class AracastCaptureService {

  private static final Pattern SIZE = Pattern.compile("(\\d{3,5})x(\\d{3,5})");
  private static final String ENV = "export DISPLAY=:0 XAUTHORITY=/root/.Xauthority; ";
  private static final String DEFAULT_SIZE = "1920x1080";

  private final AracastSshClient ssh;

  /**
   * Imagen JPEG de la pantalla.
   *
   * @param host IP o nombre del player
   * @param resolution resolución de la pantalla, o {@code null} para 1920x1080
   * @param width ancho de la imagen en píxeles
   * @param quality calidad de {@code ffmpeg}, de 2 (máxima) a 31 (mínima)
   * @return los bytes del JPEG
   */
  public byte[] image(String host, String resolution, int width, int quality) {
    String command =
        ENV
            + "f=/tmp/ctv-$$.jpg; ffmpeg -loglevel error -y -f x11grab -video_size "
            + parseSize(resolution)
            + " -i :0 -frames:v 1 -vf scale="
            + width
            + ":-2 -q:v "
            + quality
            + " \"$f\" && cat \"$f\"; rc=$?; rm -f \"$f\"; exit $rc";
    return ssh.exec(host, command, 30);
  }

  /**
   * Extrae {@code ANCHOxALTO} de un texto como {@code 1920x1080 @60.0}.
   *
   * @param resolution texto con la resolución, o {@code null}
   * @return la resolución, o 1920x1080 si no hay ninguna
   */
  public static String parseSize(String resolution) {
    if (resolution != null) {
      Matcher matcher = SIZE.matcher(resolution);
      if (matcher.find()) {
        return matcher.group(1) + "x" + matcher.group(2);
      }
    }
    return DEFAULT_SIZE;
  }
}
