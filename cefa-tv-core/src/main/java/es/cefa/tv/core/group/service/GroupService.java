package es.cefa.tv.core.group.service;

import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
import es.cefa.tv.core.group.dto.GroupRequest;
import es.cefa.tv.core.group.dto.GroupResponse;
import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.player.model.IdleLogo;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión de grupos de reproductores. */
@Service
@RequiredArgsConstructor
public class GroupService {

  private final PlayerGroupRepository groups;
  private final PlayerRepository players;
  private final PlaylistRepository playlists;
  private final SiteRepository sites;
  private final GroupSiteService groupSites;
  private final GroupIdleLogoService groupLogos;
  private final ApplicationEventPublisher events;
  private final AccessService access;
  private final AuthenticatedUserLoader loader;

  /**
   * Lista los grupos.
   *
   * @return los grupos con su número de reproductores
   */
  @Transactional(readOnly = true)
  public List<GroupResponse> list() {
    Map<UUID, String> siteNames = siteNames();
    return groups.findAll().stream()
        .filter(group -> access.canViewGroup(group.getId()))
        .map(group -> toResponse(group, siteNames))
        .toList();
  }

  /**
   * Crea un grupo.
   *
   * @param request datos del grupo
   * @return el grupo creado
   */
  @Transactional
  public GroupResponse create(GroupRequest request) {
    access.requireAdmin();
    if (groups.existsByName(request.name())) {
      throw ApiException.conflict("GROUP_NAME_EXISTS", "Ya existe un grupo con ese nombre");
    }
    PlayerGroup group = new PlayerGroup();
    group.setId(UUID.randomUUID());
    apply(group, request);
    GroupResponse response = save(group);
    afterSiteChange(group, null);
    if (group.getIdleLogo() != null) {
      groupLogos.record(group, null, 0);
    }
    return response;
  }

  /**
   * Modifica un grupo.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return el grupo actualizado
   */
  @Transactional
  public GroupResponse update(UUID id, GroupRequest request) {
    PlayerGroup group = groups.findById(id).orElseThrow(() -> ApiException.notFound("Grupo"));
    boolean siteChange = siteChanges(group, request);
    boolean logoChange = idleLogoChanges(group, request);
    if (siteChange || logoChange) {
      access.requireAdmin();
    }
    if (!group.getName().equals(request.name()) || appearanceChanges(group, request)) {
      access.requireAnyPermission(Permission.PLAYER_MANAGE);
    }
    if (!java.util.Objects.equals(group.getFallbackPlaylistId(), request.fallbackPlaylistId())) {
      access.requireAnyPermission(Permission.SCHEDULE_MANAGE);
    }
    if (!group.getName().equals(request.name()) && groups.existsByName(request.name())) {
      throw ApiException.conflict("GROUP_NAME_EXISTS", "Ya existe un grupo con ese nombre");
    }
    UUID previousSite = group.getSiteId();
    IdleLogo previousLogo = group.getIdleLogo();
    apply(group, request);
    GroupResponse response = save(group);
    if (siteChange) {
      afterSiteChange(group, previousSite);
    }
    if (logoChange) {
      groupLogos.record(group, previousLogo, groupLogos.push(group));
    }
    return response;
  }

  /**
   * Borra un grupo; sus reproductores quedan sin grupo.
   *
   * @param id identificador
   */
  @Transactional
  public void delete(UUID id) {
    access.requireAdmin();
    PlayerGroup group = groups.findById(id).orElseThrow(() -> ApiException.notFound("Grupo"));
    List<UUID> losingLogo = group.getIdleLogo() == null ? List.of() : groupLogos.inheritingIds(id);
    players.clearGroup(id);
    groups.delete(group);
    groupLogos.pushTo(losingLogo);
    loader.invalidateAllAfterCommit();
    events.publishEvent(new ContentChangedEvent("group-delete"));
  }

  /** Aplica los datos de la petición al grupo validando la playlist de reposo. */
  private void apply(PlayerGroup group, GroupRequest request) {
    if (request.fallbackPlaylistId() != null
        && !playlists.existsById(request.fallbackPlaylistId())) {
      throw ApiException.badRequest("PLAYLIST_NOT_FOUND", "Playlist de reposo inexistente");
    }
    group.setName(request.name());
    group.setFallbackPlaylistId(request.fallbackPlaylistId());
    if (request.icon() != null) {
      group.setIcon(request.icon().isEmpty() ? null : request.icon());
    }
    if (request.color() != null) {
      group.setColor(request.color().isEmpty() ? null : request.color());
    }
    if (Boolean.TRUE.equals(request.clearIdleLogo())) {
      group.setIdleLogo(null);
    } else if (request.idleLogo() != null) {
      group.setIdleLogo(request.idleLogo());
    }
    if (Boolean.TRUE.equals(request.clearSite())) {
      group.setSiteId(null);
    } else if (request.siteId() != null) {
      if (!sites.existsById(request.siteId())) {
        throw ApiException.badRequest("SITE_NOT_FOUND", "Sede inexistente");
      }
      group.setSiteId(request.siteId());
    }
  }

  /** Indica si la petición cambia el logo de la pantalla sin programación del grupo. */
  private static boolean idleLogoChanges(PlayerGroup group, GroupRequest request) {
    if (Boolean.TRUE.equals(request.clearIdleLogo())) {
      return group.getIdleLogo() != null;
    }
    return request.idleLogo() != null && request.idleLogo() != group.getIdleLogo();
  }

  /** Indica si la petición cambia la sede del grupo. */
  private static boolean siteChanges(PlayerGroup group, GroupRequest request) {
    if (Boolean.TRUE.equals(request.clearSite())) {
      return group.getSiteId() != null;
    }
    return request.siteId() != null && !request.siteId().equals(group.getSiteId());
  }

  /**
   * Pasa los reproductores a la sede del grupo, audita el cambio y refresca los permisos. Al quitar
   * la sede del grupo los reproductores conservan la suya.
   */
  private void afterSiteChange(PlayerGroup group, UUID previousSite) {
    int affected = groupSites.propagate(group);
    if (group.getSiteId() != null || previousSite != null) {
      groupSites.recordGroupAssignment(group, previousSite, affected);
    }
    loader.invalidateAllAfterCommit();
  }

  /** Indica si la petición cambia el icono o el color del grupo. */
  private static boolean appearanceChanges(PlayerGroup group, GroupRequest request) {
    return changes(group.getIcon(), request.icon()) || changes(group.getColor(), request.color());
  }

  /** Indica si un valor opcional pedido difiere del actual; nulo significa conservar. */
  private static boolean changes(String current, String requested) {
    return requested != null && !requested.equals(current == null ? "" : current);
  }

  /** Guarda el grupo y avisa del cambio de contenido. */
  private GroupResponse save(PlayerGroup group) {
    PlayerGroup saved = groups.save(group);
    events.publishEvent(new ContentChangedEvent("group-save"));
    return toResponse(saved);
  }

  /** Convierte un grupo en su DTO consultando el nombre de su sede. */
  private GroupResponse toResponse(PlayerGroup group) {
    return toResponse(
        group,
        group.getSiteId() == null
            ? Map.of()
            : sites
                .findById(group.getSiteId())
                .map(site -> Map.of(site.getId(), site.getName()))
                .orElse(Map.of()));
  }

  /** Convierte un grupo en su DTO con el número de reproductores y el nombre de su sede. */
  private GroupResponse toResponse(PlayerGroup group, Map<UUID, String> siteNames) {
    return new GroupResponse(
        group.getId(),
        group.getName(),
        group.getFallbackPlaylistId(),
        players.countByGroupId(group.getId()),
        group.getIcon(),
        group.getColor(),
        group.getSiteId(),
        group.getSiteId() == null ? null : siteNames.get(group.getSiteId()),
        group.getIdleLogo());
  }

  /** Carga los nombres de todas las sedes por identificador. */
  private Map<UUID, String> siteNames() {
    Map<UUID, String> names = new HashMap<>();
    sites.findAll().forEach(site -> names.put(site.getId(), site.getName()));
    return names;
  }
}
