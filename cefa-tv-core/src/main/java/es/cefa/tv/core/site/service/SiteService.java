package es.cefa.tv.core.site.service;

import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerPresence;
import es.cefa.tv.core.schedule.repository.ScheduleRepository;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shared.util.Ipv4Range;
import es.cefa.tv.core.site.dto.SiteRequest;
import es.cefa.tv.core.site.dto.SiteResponse;
import es.cefa.tv.core.site.model.Site;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de sedes. Solo un administrador las crea, edita y borra; las puede ver quien tenga acceso
 * a alguno de sus reproductores o una concesión sobre la sede.
 */
@Service
@RequiredArgsConstructor
public class SiteService {

  private static final String NO_SITE = "none";

  private final SiteRepository sites;
  private final PlayerRepository players;
  private final PlayerGroupRepository groups;
  private final ScheduleRepository schedules;
  private final PlayerPresence presence;
  private final AccessService access;
  private final AuthenticatedUserLoader loader;
  private final ApplicationEventPublisher events;

  /**
   * Lista las sedes visibles para el usuario con el número de reproductores que ve en cada una.
   *
   * @return las sedes ordenadas por nombre
   */
  @Transactional(readOnly = true)
  public List<SiteResponse> list() {
    return sites.findAllByOrderByNameAsc().stream()
        .filter(this::isVisible)
        .map(this::toResponse)
        .toList();
  }

  /**
   * Obtiene una sede visible para el usuario.
   *
   * @param id identificador
   * @return la sede
   */
  @Transactional(readOnly = true)
  public SiteResponse get(UUID id) {
    Site site = find(id);
    if (!isVisible(site)) {
      throw ApiException.forbidden("SCOPE_DENIED", "No tienes acceso a esta sede");
    }
    return toResponse(site);
  }

  /**
   * Crea una sede.
   *
   * @param request datos de la sede
   * @return la sede creada
   */
  @Transactional
  public SiteResponse create(SiteRequest request) {
    access.requireAdmin();
    Site site = new Site();
    site.setId(UUID.randomUUID());
    site.setCreatedAt(Instant.now());
    apply(site, request);
    return toResponse(sites.save(site));
  }

  /**
   * Modifica una sede; si cambia su zona horaria se recalcula la programación de sus reproductores.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return la sede actualizada
   */
  @Transactional
  public SiteResponse update(UUID id, SiteRequest request) {
    access.requireAdmin();
    Site site = find(id);
    apply(site, request);
    events.publishEvent(new ContentChangedEvent("site-update"));
    return toResponse(site);
  }

  /**
   * Borra una sede.
   *
   * @param id identificador
   * @param reassignTo sede a la que pasan sus reproductores, {@code none} para dejarlos sin sede, o
   *     {@code null} para exigir que no tenga ninguno
   */
  @Transactional
  public void delete(UUID id, String reassignTo) {
    access.requireAdmin();
    Site site = find(id);
    if (schedules.existsTargetForSite(id)) {
      throw ApiException.conflict(
          "SITE_SCHEDULED", "Hay programaciones que apuntan a esta sede; cámbialas antes");
    }
    boolean hasGroups = groups.existsBySiteId(id);
    if (hasGroups && reassignTo == null) {
      throw ApiException.conflict(
          "SITE_HAS_GROUPS", "La sede tiene grupos de reproductores asignados");
    }
    boolean hasPlayers = players.existsBySiteId(id);
    if (hasPlayers && reassignTo == null) {
      throw ApiException.conflict("SITE_NOT_EMPTY", "La sede tiene reproductores");
    }
    if (hasGroups || hasPlayers) {
      UUID destination = destination(id, reassignTo);
      groups.moveSite(id, destination);
      players.moveSite(id, destination);
    }
    sites.delete(site);
    loader.invalidateAllAfterCommit();
    events.publishEvent(new ContentChangedEvent("site-delete"));
  }

  /** Interpreta el destino de los reproductores de una sede que se borra. */
  private UUID destination(UUID deleted, String reassignTo) {
    if (NO_SITE.equalsIgnoreCase(reassignTo.trim())) {
      return null;
    }
    UUID target;
    try {
      target = UUID.fromString(reassignTo.trim());
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_ID", "Identificador de sede no válido");
    }
    if (target.equals(deleted)) {
      throw ApiException.badRequest(
          "SITE_REASSIGN_INVALID", "No se puede reasignar a la misma sede");
    }
    find(target);
    return target;
  }

  /** Busca una sede o lanza un 404. */
  private Site find(UUID id) {
    return sites.findById(id).orElseThrow(() -> ApiException.notFound("Sede"));
  }

  /** Valida y aplica los datos de la petición a la sede. */
  private void apply(Site site, SiteRequest request) {
    String name = request.name().trim();
    if (name.isEmpty()) {
      throw ApiException.badRequest("SITE_NAME_REQUIRED", "El nombre no puede estar vacío");
    }
    boolean taken =
        sites.findByNameIgnoreCase(name).stream()
            .anyMatch(existing -> !existing.getId().equals(site.getId()));
    if (taken) {
      throw ApiException.conflict("SITE_NAME_EXISTS", "Ya existe una sede con ese nombre");
    }
    site.setName(name);
    site.setDescription(blankToNull(request.description()));
    site.setAddress(blankToNull(request.address()));
    site.setCity(blankToNull(request.city()));
    site.setCountry(blankToNull(request.country()));
    site.setTimezone(timezone(request.timezone()));
    site.setIpRanges(ipRanges(request.ipRanges()));
  }

  /** Valida los rangos de direcciones y descarta los vacíos y los repetidos. */
  private static List<String> ipRanges(List<String> raw) {
    if (raw == null) {
      return null;
    }
    List<String> ranges = new java.util.ArrayList<>();
    for (String range : raw) {
      if (range == null || range.isBlank()) {
        continue;
      }
      String clean = range.trim();
      try {
        Ipv4Range.parse(clean);
      } catch (IllegalArgumentException e) {
        throw ApiException.badRequest(
            "INVALID_IP_RANGE", "Rango de direcciones no válido: " + clean);
      }
      if (!ranges.contains(clean)) {
        ranges.add(clean);
      }
    }
    return ranges.isEmpty() ? null : ranges;
  }

  /** Valida una zona horaria IANA; vacía significa la por defecto. */
  private static String timezone(String raw) {
    if (raw == null || raw.isBlank()) {
      return Site.DEFAULT_TIMEZONE;
    }
    String zone = raw.trim();
    if (!ZoneId.getAvailableZoneIds().contains(zone)) {
      throw ApiException.badRequest("INVALID_TIMEZONE", "Zona horaria IANA no válida: " + zone);
    }
    return zone;
  }

  /** Devuelve el texto recortado o {@code null} si está vacío. */
  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Indica si el usuario ve la sede: la administra, tiene concesión o ve alguno de sus equipos. */
  private boolean isVisible(Site site) {
    if (access.canAccessSite(site.getId(), AccessLevel.VIEW)) {
      return true;
    }
    return players.findBySiteId(site.getId()).stream().anyMatch(access::canViewPlayer);
  }

  /** Convierte una sede en su DTO contando solo los reproductores visibles. */
  private SiteResponse toResponse(Site site) {
    List<Player> visible =
        players.findBySiteId(site.getId()).stream().filter(access::canViewPlayer).toList();
    return new SiteResponse(
        site.getId(),
        site.getName(),
        site.getDescription(),
        site.getAddress(),
        site.getCity(),
        site.getCountry(),
        site.getTimezone(),
        site.getIpRanges() == null ? List.of() : site.getIpRanges(),
        visible.size(),
        visible.stream().filter(presence::isOnline).count(),
        site.getCreatedAt());
  }
}
