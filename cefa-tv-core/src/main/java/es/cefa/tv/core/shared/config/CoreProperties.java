package es.cefa.tv.core.shared.config;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración de CEFA TV (prefijo {@code cefa.tv}).
 *
 * @param publicUrl URL del core visible por los reproductores (opcional)
 * @param jwt configuración de la sesión de administración
 * @param admin credenciales del administrador inicial
 * @param storage configuración del almacenamiento de objetos
 * @param media configuración del procesado de medios
 * @param manifest configuración del cálculo de manifests
 * @param player configuración de la relación con los reproductores
 * @param shell configuración de la terminal remota
 * @param appBaseUrl URL pública de la interfaz web, destino de las redirecciones tras iniciar
 *     sesión
 * @param microsoft configuración del inicio de sesión con Microsoft Entra ID
 */
@ConfigurationProperties(prefix = "cefa.tv")
public record CoreProperties(
    String publicUrl,
    Jwt jwt,
    Admin admin,
    Storage storage,
    Media media,
    Manifest manifest,
    Player player,
    Shell shell,
    String appBaseUrl,
    Microsoft microsoft) {

  /**
   * Configuración del JWT de sesión.
   *
   * @param secret secreto de firma (mínimo 32 caracteres)
   * @param ttlHours duración de la sesión en horas
   * @param cookieSecure si la cookie de sesión exige HTTPS
   */
  public record Jwt(String secret, int ttlHours, boolean cookieSecure) {}

  /**
   * Administrador inicial.
   *
   * @param username usuario
   * @param password contraseña en claro, solo para la creación inicial
   */
  public record Admin(String username, String password) {}

  /**
   * Conexión con el almacenamiento de objetos compatible con S3 (Garage).
   *
   * @param endpoint URL de la API S3
   * @param accessKey clave de acceso
   * @param secretKey clave secreta
   * @param bucket bucket donde se guardan los medios
   * @param region región S3 con la que se firman las peticiones ({@code garage} en Garage)
   */
  public record Storage(
      String endpoint, String accessKey, String secretKey, String bucket, String region) {}

  /**
   * Procesado de medios.
   *
   * @param ffmpegPath ruta del ejecutable ffmpeg
   * @param ffprobePath ruta del ejecutable ffprobe
   * @param pdfDpi resolución de rasterizado de las páginas de PDF
   * @param tempDir directorio temporal para las subidas (vacío usa el del sistema)
   */
  public record Media(String ffmpegPath, String ffprobePath, int pdfDpi, String tempDir) {}

  /**
   * Cálculo de manifests.
   *
   * @param debounceMs milisegundos de espera para agrupar cambios consecutivos
   */
  public record Manifest(long debounceMs) {}

  /**
   * Relación con los reproductores.
   *
   * @param pollIntervalSec intervalo de sondeo que se indica a los reproductores
   * @param onlineWindowSec ventana tras el último contacto durante la que se considera en línea
   * @param neverConnectedFlagDays días desde el alta tras los que un reproductor que nunca se ha
   *     conectado se marca como tal
   * @param neverConnectedDeleteDays días desde el alta tras los que se elimina un reproductor que
   *     nunca se ha conectado
   * @param repoUrl dirección del repositorio git del reproductor, que se muestra en las
   *     instrucciones de instalación
   */
  public record Player(
      int pollIntervalSec,
      int onlineWindowSec,
      int neverConnectedFlagDays,
      int neverConnectedDeleteDays,
      String repoUrl) {}

  /**
   * Terminal remota.
   *
   * @param idleTimeoutSec segundos sin entrada del usuario tras los que se cierra la sesión
   * @param maxDurationSec duración máxima de una sesión en segundos
   */
  public record Shell(int idleTimeoutSec, int maxDurationSec) {}

  /**
   * Inicio de sesión con Microsoft Entra ID. Queda apagado mientras falte alguno de los datos del
   * registro de la aplicación o el tenant no sea un identificador de directorio.
   *
   * @param tenantId identificador del directorio (GUID) de Entra ID
   * @param clientId identificador de la aplicación registrada
   * @param clientSecret secreto de cliente de la aplicación registrada
   * @param redirectUri URI de redirección; vacío usa la plantilla de Spring con la URL base
   * @param autoProvision si se crea automáticamente el usuario que no exista, sin grupos
   * @param defaultGroupId grupo de usuarios al que se añade el usuario creado automáticamente;
   *     vacío para no añadirlo a ninguno. Nunca puede ser un grupo con acceso total
   */
  public record Microsoft(
      String tenantId,
      String clientId,
      String clientSecret,
      String redirectUri,
      boolean autoProvision,
      String defaultGroupId) {

    private static final Pattern GUID =
        Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * Indica si el inicio de sesión con Microsoft está listo para usarse.
     *
     * @return {@code true} si hay tenant (GUID), cliente y secreto reales
     */
    public boolean enabled() {
      return isPresent(clientId)
          && isPresent(clientSecret)
          && tenantId != null
          && GUID.matcher(tenantId.trim()).matches();
    }

    /**
     * URI de redirección efectiva.
     *
     * @return la configurada o la plantilla por defecto de Spring
     */
    public String effectiveRedirectUri() {
      return redirectUri == null || redirectUri.isBlank()
          ? "{baseUrl}/login/oauth2/code/{registrationId}"
          : redirectUri.trim();
    }

    private static boolean isPresent(String value) {
      return value != null && !value.isBlank() && !value.startsWith("changeme");
    }
  }
}
