package es.cefa.tv.core.aracast.service;

import es.cefa.tv.core.aracast.client.AracastSshClient;
import es.cefa.tv.core.aracast.dto.AracastCredentialsRequest;
import es.cefa.tv.core.aracast.dto.AracastPlayerResponse;
import es.cefa.tv.core.aracast.dto.CreateAracastPlayerRequest;
import es.cefa.tv.core.aracast.dto.PlayerStatus;
import es.cefa.tv.core.aracast.exception.AracastAuthException;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.exception.PlayerOfflineException;
import es.cefa.tv.core.aracast.model.AracastPlayer;
import es.cefa.tv.core.aracast.plan.AracastTemplateNames;
import es.cefa.tv.core.aracast.remote.AracastBridge;
import es.cefa.tv.core.aracast.repository.AracastPlayerRepository;
import es.cefa.tv.core.aracast.security.AracastCredentials;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.delivery.service.DeliveryService;
import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.group.service.GroupSiteService;
import es.cefa.tv.core.manifest.model.ManifestDocument;
import es.cefa.tv.core.manifest.service.ManifestService;
import es.cefa.tv.core.player.dto.PlayerResponse;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerKind;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerService;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta de players Aracast y actualización de su estado a partir de lo que informan. */
@Service
@RequiredArgsConstructor
public class AracastPlayerService {

  /** Resumen que marca un calendario congelado por el control remoto; ningún plan lo produce. */
  public static final String HELD_HASH = "held";

  private static final String CUSTOM_CFG = "/root/.aracast/data/custom.cfg";
  private static final Pattern FTP_PASSWORD =
      Pattern.compile("(?ims)^\\[FTP\\].*?^password\\s*=\\s*(\\S.*?)\\s*$");
  private static final Pattern RESOLUTION = Pattern.compile("\\d{3,5}x\\d{3,5}");

  private final AracastQueryService query;
  private final AracastPlayerRepository aracasts;
  private final PlayerRepository players;
  private final PlayerGroupRepository groups;
  private final SiteRepository sites;
  private final GroupSiteService groupSites;
  private final AccessService access;
  private final PlayerService playerService;
  private final ManifestService manifestService;
  private final ApplicationEventPublisher events;
  private final AracastSshClient ssh;
  private final AracastCredentials credentials;
  private final DeliveryService deliveries;
  private final AracastBridge bridge;

  /**
   * Consulta un player sin darlo de alta, para confirmar que responde y qué es.
   *
   * @param host IP o nombre del player
   * @return su estado
   */
  public PlayerStatus probe(String host) {
    try {
      return query.getStatus(host.trim());
    } catch (PlayerOfflineException e) {
      throw ApiException.unprocessable(
          "ARACAST_UNREACHABLE", "El player " + host + " no responde en la red");
    } catch (AracastException e) {
      throw ApiException.unprocessable(
          "ARACAST_INVALID_RESPONSE", "El equipo " + host + " no parece un player Aracast");
    }
  }

  /**
   * Da de alta un player Aracast como reproductor de CEFA TV.
   *
   * @param request datos del alta
   * @return el reproductor creado
   */
  @Transactional
  public PlayerResponse create(CreateAracastPlayerRequest request) {
    requireScope(request);
    String host = request.host().trim();
    if (aracasts.findByHostIgnoreCase(host).isPresent()) {
      throw ApiException.conflict("ARACAST_EXISTS", "Ese player Aracast ya está dado de alta");
    }
    PlayerStatus status = probe(host);
    Instant now = Instant.now();
    Player player = new Player();
    player.setId(UUID.randomUUID());
    player.setKind(PlayerKind.ARACAST);
    player.setName(uniqueName(displayName(request, host)));
    player.setDescription(blankToNull(request.description()));
    player.setGroupId(request.groupId());
    player.setSiteId(request.siteId());
    groupSites.inheritOnCreate(player);
    player.setStatus(es.cefa.tv.core.player.model.PlayerStatus.ACTIVE);
    player.setCreatedAt(now);
    AracastPlayer aracast = new AracastPlayer();
    aracast.setPlayerId(player.getId());
    aracast.setHost(host);
    aracast.setCreatedAt(now);
    apply(player, aracast, status, now);
    configureCredentials(aracast, request.sshPassword(), request.ftpPassword());
    players.saveAndFlush(player);
    aracasts.save(aracast);
    bridge.register(player.getId(), host);
    manifestService.rebuild(player.getId(), false);
    events.publishEvent(new ContentChangedEvent("player-aracast-added"));
    return playerService.toResponse(player, groupNames(player.getGroupId()));
  }

  /**
   * Datos de conexión de un Aracast, sin contraseñas.
   *
   * @param playerId reproductor
   * @return los datos
   */
  @Transactional(readOnly = true)
  public AracastPlayerResponse get(UUID playerId) {
    AracastPlayer aracast = find(playerId);
    return new AracastPlayerResponse(
        playerId,
        aracast.getHost(),
        aracast.getMac(),
        aracast.getModel(),
        aracast.getSshPasswordEnc() != null,
        aracast.getFtpPasswordEnc() != null);
  }

  /**
   * Cambia las contraseñas propias de un Aracast. Una SSH nueva se comprueba contra el player antes
   * de guardarla.
   *
   * @param playerId reproductor
   * @param request contraseñas nuevas
   * @return los datos actualizados
   */
  @Transactional
  public AracastPlayerResponse updateCredentials(UUID playerId, AracastCredentialsRequest request) {
    AracastPlayer aracast = find(playerId);
    configureCredentials(aracast, request.sshPassword(), request.ftpPassword());
    return get(playerId);
  }

  /**
   * Guarda lo que un Aracast acaba de informar: versión, red, resolución, zona horaria y disco.
   *
   * @param playerId reproductor
   * @param status estado leído del player
   */
  @Transactional
  public void applyStatus(UUID playerId, PlayerStatus status) {
    Player player = players.findById(playerId).orElse(null);
    AracastPlayer aracast = aracasts.findById(playerId).orElse(null);
    if (player == null || aracast == null) {
      return;
    }
    Instant now = Instant.now();
    apply(player, aracast, status, now);
    updateNowPlaying(player, status.currentTemplate());
  }

  /**
   * Si el reproductor aún lleva el nombre provisional "Aracast &lt;ip&gt;", le pone el nombre que
   * tiene realmente el equipo.
   *
   * @param playerId reproductor
   * @param hostname consulta del nombre configurado en el player, que solo se hace si hace falta
   */
  @Transactional
  public void adoptHostname(UUID playerId, java.util.function.Supplier<String> hostname) {
    Player player = players.findById(playerId).orElse(null);
    if (player == null
        || !player.getName().matches("(?i)Aracast \\d{1,3}(\\.\\d{1,3}){3}( \\d+)?")) {
      return;
    }
    String real = blankToNull(hostname.get());
    if (real == null) {
      return;
    }
    if (!real.equals(player.getName())) {
      player.setName(uniqueName(real));
      players.save(player);
    }
  }

  /**
   * Anota que una versión se ha publicado en el player.
   *
   * @param playerId reproductor
   * @param version versión publicada
   * @param planHash resumen del plan publicado
   */
  @Transactional
  public void recordPublished(UUID playerId, long version, String planHash) {
    Player player = players.findById(playerId).orElse(null);
    AracastPlayer aracast = aracasts.findById(playerId).orElse(null);
    if (player == null || aracast == null) {
      return;
    }
    if (version > player.getAppliedVersion()) {
      player.setAppliedVersion(version);
    }
    player.setLastError(null);
    aracast.setPublishedHash(planHash);
    aracast.setPublishedAt(Instant.now());
    deliveries.markApplied(playerId, version);
  }

  /**
   * Anota que el calendario del player está congelado por una sesión de control remoto. El resumen
   * deja de coincidir con el de cualquier plan, así que el siguiente repaso de la entrega publica
   * el calendario real, incluso si el servidor se reinicia mientras dura la sesión.
   *
   * @param playerId reproductor
   */
  @Transactional
  public void recordHeld(UUID playerId) {
    aracasts
        .findById(playerId)
        .ifPresent(
            aracast -> {
              aracast.setPublishedHash(HELD_HASH);
              aracast.setPublishedAt(Instant.now());
            });
  }

  /**
   * Anota que una versión no necesita publicarse: el player no tiene nada programado desde CEFA TV
   * y todavía conserva su programación anterior, que no se debe borrar.
   *
   * @param playerId reproductor
   * @param version versión que se da por aplicada
   */
  @Transactional
  public void recordSkipped(UUID playerId, long version) {
    players
        .findById(playerId)
        .ifPresent(
            player -> {
              if (version > player.getAppliedVersion()) {
                player.setAppliedVersion(version);
              }
            });
    deliveries.markApplied(playerId, version);
  }

  /**
   * Anota que no se pudo publicar en el player.
   *
   * @param playerId reproductor
   * @param version versión que falló
   * @param newVersion si la versión estaba pendiente de aplicar; si no, solo se anota el error
   * @param message motivo
   */
  @Transactional
  public void recordFailure(UUID playerId, long version, boolean newVersion, String message) {
    players.findById(playerId).ifPresent(player -> player.setLastError(message));
    if (newVersion) {
      deliveries.markFailed(playerId, version, message);
    }
  }

  private void updateNowPlaying(Player player, String currentTemplate) {
    ManifestDocument manifest = null;
    try {
      manifest = manifestService.latest(player.getId()).getManifest();
    } catch (ApiException e) {
      manifest = null;
    }
    var playing = AracastTemplateNames.resolve(currentTemplate, manifest);
    if (playing.isPresent()) {
      player.setState("PLAYING");
      player.setCurrentPlaylistId(UUID.fromString(playing.get().playlistId()));
      player.setCurrentAssetId(
          playing.get().assetId() == null ? null : UUID.fromString(playing.get().assetId()));
    } else {
      boolean idle = currentTemplate == null || "basic_json".equals(currentTemplate);
      player.setState(idle ? "IDLE" : "PLAYING");
      player.setCurrentPlaylistId(null);
      player.setCurrentAssetId(null);
    }
  }

  private void apply(Player player, AracastPlayer aracast, PlayerStatus status, Instant now) {
    player.markSeen(now);
    bridge.markSeen(player.getId(), aracast.getHost());
    player.setIp(aracast.getHost());
    player.setPlayerVersion(status.version());
    if (status.resolution() != null && RESOLUTION.matcher(status.resolution()).matches()) {
      player.setResolution(status.resolution());
    }
    if (status.timezone() != null) {
      player.setTimezone(status.timezone());
    }
    Map<String, Object> hardware = new LinkedHashMap<>();
    hardware.put("model", status.model());
    hardware.put("mac", status.mac());
    hardware.put("uptime", status.uptime());
    hardware.put("diskTotalBytes", status.totalBytes());
    hardware.put("diskFreeBytes", status.freeBytes());
    hardware.put("currentTemplate", status.currentTemplate());
    player.setHardware(hardware);
    aracast.setMac(status.mac());
    aracast.setModel(status.model());
  }

  private AracastPlayer find(UUID playerId) {
    return aracasts
        .findById(playerId)
        .orElseThrow(() -> ApiException.notFound("Reproductor Aracast"));
  }

  private void configureCredentials(AracastPlayer aracast, String sshPassword, String ftpPassword) {
    String ftpToStore = ftpPassword;
    if (sshPassword != null && !sshPassword.isEmpty()) {
      String config = readCustomConfig(aracast.getHost(), sshPassword);
      if (ftpPassword == null) {
        ftpToStore = ftpPasswordFrom(config);
      }
    }
    credentials.store(aracast, sshPassword, ftpToStore);
  }

  private String readCustomConfig(String host, String sshPassword) {
    try {
      return ssh.execTextWithPassword(
          host, sshPassword, "cat " + CUSTOM_CFG + " 2>/dev/null; true", 15);
    } catch (AracastAuthException e) {
      throw ApiException.unprocessable(
          "ARACAST_SSH_AUTH_FAILED", "El player " + host + " rechazó la contraseña SSH");
    } catch (PlayerOfflineException e) {
      throw ApiException.unprocessable(
          "ARACAST_UNREACHABLE", "El player " + host + " no responde por SSH");
    } catch (AracastException e) {
      throw ApiException.unprocessable(
          "ARACAST_SSH_ERROR", "No se pudo comprobar la contraseña SSH de " + host);
    }
  }

  /**
   * Extrae la contraseña de la sección {@code [FTP]} de {@code custom.cfg}.
   *
   * @param config contenido del fichero
   * @return la contraseña, o {@code null} si no aparece
   */
  static String ftpPasswordFrom(String config) {
    var matcher = FTP_PASSWORD.matcher(config);
    return matcher.find() ? matcher.group(1) : null;
  }

  private void requireScope(CreateAracastPlayerRequest request) {
    if (request.groupId() == null && request.siteId() == null) {
      access.requireAdmin();
    }
    if (request.groupId() != null) {
      PlayerGroup group =
          groups
              .findById(request.groupId())
              .orElseThrow(() -> ApiException.badRequest("GROUP_NOT_FOUND", "Grupo inexistente"));
      access.requireGroupLevel(group.getId(), AccessLevel.OPERATE);
    }
    if (request.siteId() != null) {
      if (!sites.existsById(request.siteId())) {
        throw ApiException.badRequest("SITE_NOT_FOUND", "Sede inexistente");
      }
      access.requireSiteLevel(request.siteId(), AccessLevel.OPERATE);
    }
  }

  private static String displayName(CreateAracastPlayerRequest request, String host) {
    return request.name() != null && !request.name().isBlank()
        ? request.name().trim()
        : "Aracast " + host;
  }

  private String uniqueName(String base) {
    String candidate = base;
    int suffix = 2;
    while (players.existsByName(candidate)) {
      candidate = base + " " + suffix++;
    }
    return candidate;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private Map<UUID, String> groupNames(UUID groupId) {
    if (groupId == null) {
      return Map.of();
    }
    return groups.findById(groupId).map(g -> Map.of(g.getId(), g.getName())).orElse(Map.of());
  }
}
