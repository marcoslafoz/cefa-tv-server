package es.cefa.tv.core.player.service;

import es.cefa.tv.core.aracast.service.AracastCommandService;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.delivery.dto.DeliveryResponse;
import es.cefa.tv.core.delivery.service.DeliveryService;
import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.manifest.service.ManifestService;
import es.cefa.tv.core.media.repository.MediaAssetRepository;
import es.cefa.tv.core.player.dto.PlayerResponse;
import es.cefa.tv.core.player.dto.PlayerUpdateRequest;
import es.cefa.tv.core.player.model.CommandType;
import es.cefa.tv.core.player.model.IdleLogo;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerConfig;
import es.cefa.tv.core.player.model.PlayerKind;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import es.cefa.tv.core.preview.service.PreviewService;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.site.repository.SiteRepository;
import es.cefa.tv.core.telemetry.service.DisplayResolutions;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión administrativa de reproductores. */
@Service
@RequiredArgsConstructor
public class PlayerService {

  private final PlayerRepository players;
  private final PlayerGroupRepository groups;
  private final IdleLogoResolver idleLogos;
  private final PlaylistRepository playlists;
  private final PlayerPresence presence;
  private final PlayerCommandService commandService;
  private final PlayerSessionRegistry sessions;
  private final ManifestService manifestService;
  private final DeliveryService deliveryService;
  private final ApplicationEventPublisher events;
  private final AccessService access;
  private final CoreProperties properties;
  private final SiteRepository sites;
  private final MediaAssetRepository assets;
  private final PreviewService previews;
  private final AracastCommandService aracastCommands;

  /**
   * Lista los reproductores visibles, de una sede o de todas.
   *
   * @param siteId sede a filtrar, o {@code null} para todas
   * @return los reproductores
   */
  @Transactional(readOnly = true)
  public List<PlayerResponse> list(UUID siteId) {
    Map<UUID, String> groupNames = groupNames();
    Map<UUID, String> siteNames = siteNames();
    List<Player> visible =
        players.findAll().stream()
            .filter(access::canViewPlayer)
            .filter(p -> siteId == null || siteId.equals(p.getSiteId()))
            .toList();
    Map<UUID, String> thumbnails = currentThumbnails(visible);
    Map<UUID, IdleLogo> groupLogos = idleLogos.groupLogos();
    return visible.stream()
        .map(p -> toResponse(p, groupNames, siteNames, thumbnails, groupLogos))
        .toList();
  }

  /**
   * Obtiene un reproductor.
   *
   * @param id identificador
   * @return el reproductor
   */
  @Transactional(readOnly = true)
  public PlayerResponse get(UUID id) {
    return toResponse(find(id), groupNames());
  }

  /**
   * Modifica un reproductor.
   *
   * @param id identificador
   * @param request campos a cambiar
   * @return el reproductor actualizado
   */
  @Transactional
  public PlayerResponse update(UUID id, PlayerUpdateRequest request) {
    Player player = find(id);
    requireUpdateRights(player, request);
    PlayerConfig previousConfig = commandService.configOf(player);
    if (request.name() != null && !request.name().isBlank()) {
      player.setName(request.name());
    }
    if (Boolean.TRUE.equals(request.clearGroup())) {
      player.setGroupId(null);
    } else if (request.groupId() != null) {
      if (!groups.existsById(request.groupId())) {
        throw ApiException.badRequest("GROUP_NOT_FOUND", "Grupo inexistente");
      }
      player.setGroupId(request.groupId());
    }
    if (Boolean.TRUE.equals(request.clearFallbackPlaylist())) {
      player.setFallbackPlaylistId(null);
    } else if (request.fallbackPlaylistId() != null) {
      if (!playlists.existsById(request.fallbackPlaylistId())) {
        throw ApiException.badRequest("PLAYLIST_NOT_FOUND", "Playlist inexistente");
      }
      player.setFallbackPlaylistId(request.fallbackPlaylistId());
    }
    if (request.orientation() != null) {
      player.setOrientation(request.orientation());
    }
    if (request.status() != null) {
      player.setStatus(request.status());
    }
    applyPreferredOutput(player, request);
    applyIdleLogo(player, request);
    applyDescriptionAndSite(player, request);
    if (!previousConfig.equals(commandService.configOf(player))) {
      commandService.sendConfig(player);
    }
    events.publishEvent(new ContentChangedEvent("player-update"));
    return toResponse(player, groupNames());
  }

  /**
   * Da de baja un reproductor, avisándole si está conectado.
   *
   * @param id identificador
   */
  @Transactional
  public void delete(UUID id) {
    Player player = find(id);
    if (player.getKind() == PlayerKind.ARACAST) {
      aracastCommands.forget(id);
    } else if (sessions.isConnected(id)) {
      commandService.send(id, CommandType.UNENROLL);
      sessions.disconnectPlayer(id);
    }
    players.delete(player);
    events.publishEvent(new ContentChangedEvent("player-delete"));
  }

  /**
   * Fuerza el recálculo y la entrega de la programación.
   *
   * @param id identificador
   */
  @Transactional
  public void resync(UUID id) {
    find(id);
    manifestService.rebuild(id, true);
  }

  /**
   * Envía una orden al reproductor.
   *
   * @param id identificador
   * @param type orden
   */
  @Transactional
  public void command(UUID id, CommandType type) {
    Player player = find(id);
    if (player.getKind() == PlayerKind.ARACAST) {
      aracastCommands.execute(id, type);
      return;
    }
    commandService.send(id, type);
  }

  /**
   * Lista el historial de entregas.
   *
   * @param id identificador
   * @return las entregas
   */
  @Transactional(readOnly = true)
  public List<DeliveryResponse> deliveries(UUID id) {
    find(id);
    return deliveryService.history(id);
  }

  /**
   * Convierte un reproductor en su DTO.
   *
   * @param player reproductor
   * @param groupNames nombres de grupo por identificador
   * @return el DTO
   */
  public PlayerResponse toResponse(Player player, Map<UUID, String> groupNames) {
    return toResponse(
        player,
        groupNames,
        player.getSiteId() == null
            ? Map.of()
            : sites
                .findById(player.getSiteId())
                .map(site -> Map.of(site.getId(), site.getName()))
                .orElse(Map.of()));
  }

  /**
   * Convierte un reproductor en su DTO.
   *
   * @param player reproductor
   * @param groupNames nombres de grupo por identificador
   * @param siteNames nombres de sede por identificador
   * @return el DTO
   */
  public PlayerResponse toResponse(
      Player player, Map<UUID, String> groupNames, Map<UUID, String> siteNames) {
    return toResponse(
        player, groupNames, siteNames, currentThumbnails(List.of(player)), idleLogos.groupLogos());
  }

  /** Convierte un reproductor en su DTO con las miniaturas de lo que suena ya calculadas. */
  private PlayerResponse toResponse(
      Player player,
      Map<UUID, String> groupNames,
      Map<UUID, String> siteNames,
      Map<UUID, String> thumbnails,
      Map<UUID, IdleLogo> groupLogos) {
    IdleLogoResolver.Effective logo = idleLogos.effective(player, groupLogos);
    return new PlayerResponse(
        player.getId(),
        player.getName(),
        player.getGroupId(),
        player.getGroupId() == null ? null : groupNames.get(player.getGroupId()),
        player.getDescription(),
        player.getSiteId(),
        player.getSiteId() == null ? null : siteNames.get(player.getSiteId()),
        player.getFallbackPlaylistId(),
        player.getStatus(),
        player.getHostname(),
        player.getIp(),
        player.getHardware(),
        player.getResolution(),
        player.getPreferredOutput(),
        player.getIdleLogo(),
        logo.logo(),
        logo.source(),
        player.getSystemInfo(),
        player.getDisplays(),
        player.getOrientation(),
        player.getPlayerVersion(),
        player.getPlayerBuild(),
        player.getTimezone(),
        presence.isOnline(player),
        player.getLastSeenAt(),
        player.getDesiredVersion(),
        player.getAppliedVersion(),
        player.isPending(),
        player.getState(),
        player.getCurrentPlaylistId(),
        player.getCurrentAssetId(),
        player.getLastError(),
        player.getCreatedAt(),
        isNeverConnected(player),
        previews.previewAt(player.getId()),
        player.getCurrentAssetId() == null ? null : thumbnails.get(player.getCurrentAssetId()),
        player.getKind());
  }

  /**
   * Indica si un reproductor lleva más de los días configurados dado de alta sin haberse conectado
   * nunca.
   *
   * @param player reproductor
   * @return {@code true} si nunca se ha conectado y ya pasó el plazo
   */
  public boolean isNeverConnected(Player player) {
    return player.getFirstConnectedAt() == null
        && player
            .getCreatedAt()
            .isBefore(
                Instant.now().minus(Duration.ofDays(properties.player().neverConnectedFlagDays())));
  }

  /** Aplica la descripción y la sede de la petición. */
  private void applyDescriptionAndSite(Player player, PlayerUpdateRequest request) {
    if (request.description() != null) {
      String description = request.description().trim();
      player.setDescription(description.isEmpty() ? null : description);
    }
    PlayerGroup group =
        player.getGroupId() == null ? null : groups.findById(player.getGroupId()).orElse(null);
    if (group != null && group.getSiteId() != null) {
      applyInheritedSite(player, group, request);
    } else if (Boolean.TRUE.equals(request.clearSite())) {
      player.setSiteId(null);
    } else if (request.siteId() != null) {
      if (!sites.existsById(request.siteId())) {
        throw ApiException.badRequest("SITE_NOT_FOUND", "Sede inexistente");
      }
      player.setSiteId(request.siteId());
    }
  }

  /**
   * Aplica la sede de un grupo que la tiene: el reproductor la hereda y no puede tener otra ni
   * quedarse sin ella mientras siga en el grupo.
   */
  private void applyInheritedSite(Player player, PlayerGroup group, PlayerUpdateRequest request) {
    boolean otherSite = request.siteId() != null && !request.siteId().equals(group.getSiteId());
    if (otherSite || Boolean.TRUE.equals(request.clearSite())) {
      throw ApiException.conflict(
          "SITE_INHERITED_FROM_GROUP",
          "La sede de este reproductor la define su grupo \"" + group.getName() + "\"");
    }
    player.setSiteId(group.getSiteId());
  }

  /** Cambia la salida de vídeo elegida. */
  private void applyPreferredOutput(Player player, PlayerUpdateRequest request) {
    if (Boolean.TRUE.equals(request.clearPreferredOutput())) {
      player.setPreferredOutput(null);
    } else if (request.preferredOutput() != null && !request.preferredOutput().isBlank()) {
      String output = request.preferredOutput().trim();
      List<Map<String, Object>> known = player.getDisplays();
      if (known != null && !known.isEmpty() && !DisplayResolutions.isConnected(known, output)) {
        throw ApiException.badRequest(
            "DISPLAY_NOT_FOUND", "La salida no existe o no está conectada");
      }
      player.setPreferredOutput(output);
    }
  }

  /** Cambia el logo propio de la pantalla sin programación; nulo deja al reproductor heredar. */
  private void applyIdleLogo(Player player, PlayerUpdateRequest request) {
    if (Boolean.TRUE.equals(request.clearIdleLogo())) {
      player.setIdleLogo(null);
    } else if (request.idleLogo() != null) {
      player.setIdleLogo(request.idleLogo());
    }
  }

  /** Exige los permisos y el alcance que requieren los campos que cambia la petición. */
  private void requireUpdateRights(Player player, PlayerUpdateRequest request) {
    boolean touchesFallback =
        request.fallbackPlaylistId() != null
            || Boolean.TRUE.equals(request.clearFallbackPlaylist());
    boolean touchesPlayer =
        (request.name() != null
                && !request.name().isBlank()
                && !request.name().equals(player.getName()))
            || (request.groupId() != null && !request.groupId().equals(player.getGroupId()))
            || (Boolean.TRUE.equals(request.clearGroup()) && player.getGroupId() != null)
            || (request.orientation() != null
                && !request.orientation().equals(player.getOrientation()))
            || (request.status() != null && request.status() != player.getStatus())
            || (request.description() != null
                && !request.description().trim().equals(nullToEmpty(player.getDescription())))
            || (request.siteId() != null && !request.siteId().equals(player.getSiteId()))
            || (Boolean.TRUE.equals(request.clearSite()) && player.getSiteId() != null)
            || (request.preferredOutput() != null && !request.preferredOutput().isBlank())
            || Boolean.TRUE.equals(request.clearPreferredOutput())
            || (request.idleLogo() != null && request.idleLogo() != player.getIdleLogo())
            || (Boolean.TRUE.equals(request.clearIdleLogo()) && player.getIdleLogo() != null);
    if (touchesPlayer) {
      access.requireAnyPermission(Permission.PLAYER_MANAGE);
    }
    if (touchesFallback) {
      access.requireAnyPermission(Permission.SCHEDULE_MANAGE);
    }
    if (request.groupId() != null) {
      access.requireGroupLevel(request.groupId(), AccessLevel.OPERATE);
    }
    if (Boolean.TRUE.equals(request.clearGroup()) && player.getGroupId() != null) {
      access.requireAdmin();
    }
    if (request.siteId() != null && !request.siteId().equals(player.getSiteId())) {
      access.requireSiteLevel(request.siteId(), AccessLevel.OPERATE);
    }
    if (Boolean.TRUE.equals(request.clearSite()) && player.getSiteId() != null) {
      access.requireAdmin();
    }
  }

  /** Sustituye un nulo por una cadena vacía. */
  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /** Calcula la miniatura de lo que está sonando en cada reproductor, indexada por medio. */
  private Map<UUID, String> currentThumbnails(List<Player> source) {
    List<UUID> ids =
        source.stream()
            .map(Player::getCurrentAssetId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, String> thumbnails = new HashMap<>();
    if (!ids.isEmpty()) {
      assets.findAllById(ids).stream()
          .filter(asset -> asset.getThumbnailKey() != null)
          .forEach(
              asset ->
                  thumbnails.put(asset.getId(), "/api/v1/media/" + asset.getId() + "/thumbnail"));
    }
    return thumbnails;
  }

  /** Carga los nombres de todas las sedes por identificador. */
  private Map<UUID, String> siteNames() {
    Map<UUID, String> names = new HashMap<>();
    sites.findAll().forEach(site -> names.put(site.getId(), site.getName()));
    return names;
  }

  /** Carga los nombres de todos los grupos por identificador. */
  private Map<UUID, String> groupNames() {
    Map<UUID, String> names = new HashMap<>();
    for (PlayerGroup group : groups.findAll()) {
      names.put(group.getId(), group.getName());
    }
    return names;
  }

  /** Busca un reproductor o lanza un 404. */
  private Player find(UUID id) {
    return players.findById(id).orElseThrow(() -> ApiException.notFound("Reproductor"));
  }
}
