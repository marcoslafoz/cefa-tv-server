package es.cefa.tv.core.aracast.plan;

import es.cefa.tv.core.manifest.model.ManifestDocument;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reconoce las plantillas que genera CEFA TV a partir de la marca {@code ~ctv-...} del nombre. */
public final class AracastTemplateNames {

  /**
   * Marca al final del nombre: ocho caracteres del identificador de la playlist y, opcional, el
   * paso. Se reconoce también el formato antiguo con corchetes, que el player no sabe usar porque
   * trata los nombres como patrones, para poder borrar esas plantillas.
   */
  public static final Pattern MARKER =
      Pattern.compile("(?:~ctv-|\\[ctv-)([0-9a-f]{8})(?:-(\\d+))?]?$");

  private AracastTemplateNames() {}

  /**
   * Indica si una plantilla del player la ha generado CEFA TV.
   *
   * @param templateName nombre de la plantilla en el player
   * @return {@code true} si lleva la marca
   */
  public static boolean isManaged(String templateName) {
    return templateName != null && MARKER.matcher(templateName).find();
  }

  /**
   * Qué se está emitiendo cuando el player informa de una plantilla en emisión.
   *
   * @param templateName plantilla en emisión
   * @param manifest manifest vigente del reproductor
   * @return la playlist y, en una secuencia, el medio del paso; vacío si no es de CEFA TV
   */
  public static Optional<Playing> resolve(String templateName, ManifestDocument manifest) {
    if (templateName == null || manifest == null || manifest.playlists() == null) {
      return Optional.empty();
    }
    Matcher matcher = MARKER.matcher(templateName);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String prefix = matcher.group(1);
    String step = matcher.group(2);
    return manifest.playlists().values().stream()
        .filter(p -> p.id().replace("-", "").startsWith(prefix))
        .findFirst()
        .map(
            playlist -> {
              String assetId = null;
              if (step != null) {
                int index = Integer.parseInt(step) - 1;
                if (index >= 0 && index < playlist.items().size()) {
                  assetId = playlist.items().get(index).assetId();
                }
              }
              return new Playing(playlist.id(), assetId);
            });
  }

  /**
   * Lo que se está emitiendo.
   *
   * @param playlistId playlist
   * @param assetId medio, si la plantilla es un paso de una secuencia
   */
  public record Playing(String playlistId, String assetId) {}
}
