package es.cefa.tv.core.schedule.service;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessPolicy;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerTimezoneResolver;
import es.cefa.tv.core.playlist.model.Playlist;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import es.cefa.tv.core.playlist.service.PlaylistService;
import es.cefa.tv.core.schedule.dto.ScheduleRequest;
import es.cefa.tv.core.schedule.dto.ScheduleResponse;
import es.cefa.tv.core.schedule.dto.TimelineOccurrence;
import es.cefa.tv.core.schedule.model.Schedule;
import es.cefa.tv.core.schedule.model.ScheduleRule;
import es.cefa.tv.core.schedule.model.ScheduleTarget;
import es.cefa.tv.core.schedule.repository.ScheduleRepository;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.site.repository.SiteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión de programaciones y cálculo del timeline. */
@Service
@RequiredArgsConstructor
public class ScheduleService {

  private static final int DEFAULT_ASSET_DURATION_SEC = 10;
  private static final int MAX_TIMELINE_DAYS = 62;
  private static final int MAX_TIMELINE_PLAYERS = 200;
  private static final UUID NO_GROUP = new UUID(0L, 0L);
  private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

  private final ScheduleRepository schedules;
  private final PlaylistRepository playlists;
  private final PlaylistService playlistService;
  private final PlayerRepository players;
  private final PlayerGroupRepository groups;
  private final SiteRepository sites;
  private final ApplicationEventPublisher events;
  private final AccessService access;
  private final PlayerTimezoneResolver timezones;
  private final Clock clock;

  /**
   * Lista todas las programaciones.
   *
   * @return las programaciones
   */
  @Transactional(readOnly = true)
  public List<ScheduleResponse> list() {
    ScheduleZones zones = loadZones();
    Instant now = clock.instant();
    return schedules.findAll().stream()
        .filter(this::isVisible)
        .map(schedule -> toResponse(schedule, zones, now))
        .toList();
  }

  /**
   * Obtiene una programación.
   *
   * @param id identificador
   * @return la programación
   */
  @Transactional(readOnly = true)
  public ScheduleResponse get(UUID id) {
    Schedule schedule = find(id);
    if (!isVisible(schedule)) {
      throw scopeDenied();
    }
    return toResponse(schedule, loadZones(), clock.instant());
  }

  /**
   * Crea una programación.
   *
   * @param request datos
   * @return la programación creada
   */
  @Transactional
  public ScheduleResponse create(ScheduleRequest request) {
    Schedule schedule = new Schedule();
    schedule.setId(UUID.randomUUID());
    return save(schedule, request, null);
  }

  /**
   * Reemplaza una programación.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return la programación actualizada
   */
  @Transactional
  public ScheduleResponse replace(UUID id, ScheduleRequest request) {
    Schedule schedule = find(id);
    requireOperateOnExisting(schedule);
    return save(schedule, request, schedule.getPlaylistId());
  }

  /**
   * Borra una programación y su playlist implícita si queda huérfana.
   *
   * @param id identificador
   */
  @Transactional
  public void delete(UUID id) {
    Schedule schedule = find(id);
    requireOperateOnExisting(schedule);
    UUID playlistId = schedule.getPlaylistId();
    schedules.delete(schedule);
    schedules.flush();
    removeOrphanHidden(playlistId);
    events.publishEvent(new ContentChangedEvent("schedule-delete"));
  }

  /**
   * Expande por día, en un rango, las programaciones de uno o varios reproductores. Los destinos
   * pueden ser reproductores, grupos o sedes; solo se incluyen los reproductores que el usuario
   * puede ver. Si se indica {@code playerId} (compatibilidad) y el usuario no puede ver ese
   * reproductor, responde 403.
   *
   * @param playerId reproductor único, o {@code null}
   * @param playerIds reproductores
   * @param groupIds grupos cuyos reproductores se incluyen
   * @param siteIds sedes cuyos reproductores se incluyen
   * @param from primer día incluido
   * @param to último día incluido
   * @return las ocurrencias ordenadas por fecha, hora y nombre de reproductor
   */
  @Transactional(readOnly = true)
  public List<TimelineOccurrence> timeline(
      UUID playerId,
      List<UUID> playerIds,
      List<UUID> groupIds,
      List<UUID> siteIds,
      LocalDate from,
      LocalDate to) {
    if (to.isBefore(from) || from.plusDays(MAX_TIMELINE_DAYS).isBefore(to)) {
      throw ApiException.badRequest("INVALID_RANGE", "Rango de fechas no válido");
    }
    List<Player> selected = selectPlayers(playerId, playerIds, groupIds, siteIds);
    Map<UUID, String> siteZones = timezones.siteTimezones();
    List<Schedule> enabled = schedules.findAllEnabledWithTargets();
    Instant now = clock.instant();
    TimelineContext context = new TimelineContext(now);
    List<TimelineOccurrence> result = new ArrayList<>();
    for (Player player : selected) {
      String timezone = timezones.timezoneOf(player, siteZones);
      ZoneId zone = PlayerTimezoneResolver.zoneOf(timezone);
      for (Schedule schedule : enabled) {
        if (reaches(schedule, player)) {
          addOccurrences(result, schedule, player, timezone, zone, from, to, context);
        }
      }
    }
    result.sort(
        Comparator.comparing(TimelineOccurrence::date)
            .thenComparing(TimelineOccurrence::startTime)
            .thenComparing(TimelineOccurrence::playerName));
    return result;
  }

  /** Reúne los reproductores pedidos, sin repetidos, filtrados por lo que el usuario puede ver. */
  private List<Player> selectPlayers(
      UUID playerId, List<UUID> playerIds, List<UUID> groupIds, List<UUID> siteIds) {
    Map<UUID, Player> selected = new LinkedHashMap<>();
    if (playerId != null) {
      access.requirePlayerLevel(playerId, AccessLevel.VIEW);
      players.findById(playerId).ifPresent(player -> selected.put(player.getId(), player));
    }
    if (playerId == null && isEmpty(playerIds) && isEmpty(groupIds) && isEmpty(siteIds)) {
      throw ApiException.badRequest(
          "TARGETS_REQUIRED", "Indica al menos un reproductor, grupo o sede");
    }
    if (playerIds != null) {
      players.findAllById(playerIds).forEach(player -> addIfVisible(selected, player));
    }
    if (groupIds != null) {
      groupIds.forEach(id -> players.findByGroupId(id).forEach(p -> addIfVisible(selected, p)));
    }
    if (siteIds != null) {
      siteIds.forEach(id -> players.findBySiteId(id).forEach(p -> addIfVisible(selected, p)));
    }
    if (selected.size() > MAX_TIMELINE_PLAYERS) {
      throw ApiException.badRequest(
          "TOO_MANY_PLAYERS",
          "Demasiados reproductores (máximo " + MAX_TIMELINE_PLAYERS + "): acota el filtro");
    }
    return selected.values().stream().sorted(Comparator.comparing(Player::getName)).toList();
  }

  /** Añade el reproductor a la selección si el usuario puede verlo. */
  private void addIfVisible(Map<UUID, Player> selected, Player player) {
    if (access.canAccessPlayer(player, AccessLevel.VIEW)) {
      selected.put(player.getId(), player);
    }
  }

  /** Indica si una lista opcional no trae ningún valor. */
  private static boolean isEmpty(List<UUID> values) {
    return values == null || values.isEmpty();
  }

  /** Indica si alguno de los destinos de la programación alcanza al reproductor. */
  private static boolean reaches(Schedule schedule, Player player) {
    return schedule.getTargets().stream()
        .anyMatch(
            target ->
                Objects.equals(target.getPlayerId(), player.getId())
                    || (target.getGroupId() != null
                        && Objects.equals(target.getGroupId(), player.getGroupId()))
                    || (target.getSiteId() != null
                        && Objects.equals(target.getSiteId(), player.getSiteId())));
  }

  /** Añade las ocurrencias de una programación para un reproductor en cada día del rango. */
  private void addOccurrences(
      List<TimelineOccurrence> result,
      Schedule schedule,
      Player player,
      String timezone,
      ZoneId zone,
      LocalDate from,
      LocalDate to,
      TimelineContext context) {
    ScheduleRule rule = ScheduleRule.from(schedule);
    PlaylistService.Summary summary = context.summary(schedule.getPlaylistId());
    String playlistName = context.playlistName(schedule.getPlaylistId());
    Instant nextRunAt = context.nextRunAt(rule, zone);
    LocalTime start = schedule.isAllDay() ? null : schedule.getStartTime();
    LocalTime end = schedule.isAllDay() ? null : schedule.getEndTime();
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      if (!rule.isActiveAt(day, rule.effectiveStart())) {
        continue;
      }
      result.add(
          new TimelineOccurrence(
              schedule.getId(),
              schedule.getName(),
              schedule.getPlaylistId(),
              playlistName,
              day,
              start == null ? "00:00" : start.format(HOUR_MINUTE),
              end == null ? "24:00" : end.format(HOUR_MINUTE),
              schedule.getPriority(),
              schedule.isAllDay(),
              summary.thumbnailUrls(),
              summary.itemCount(),
              summary.totalDurationSec(),
              player.getId(),
              player.getName(),
              timezone,
              ScheduleTiming.occurrenceStatus(day, start, end, zone, context.now()),
              nextRunAt));
    }
  }

  /** Carga el índice de reproductores y zonas horarias para las programaciones. */
  private ScheduleZones loadZones() {
    return new ScheduleZones(players.findAll(), timezones.siteTimezones(), timezones);
  }

  /** Datos que se reutilizan al expandir el timeline: instante, playlists y próximas emisiones. */
  private final class TimelineContext {

    private final Instant now;
    private final Map<UUID, Playlist> playlistCache = new HashMap<>();
    private final Map<UUID, PlaylistService.Summary> summaryCache = new HashMap<>();
    private final Map<String, Instant> nextRunCache = new HashMap<>();

    /** Crea el contexto con el instante de evaluación. */
    TimelineContext(Instant now) {
      this.now = now;
    }

    /** Instante de evaluación. */
    Instant now() {
      return now;
    }

    /** Resumen de una playlist, calculado una sola vez. */
    PlaylistService.Summary summary(UUID playlistId) {
      return summaryCache.computeIfAbsent(
          playlistId, id -> ScheduleService.summary(playlist(id).orElse(null)));
    }

    /** Nombre de una playlist, o vacío si ya no existe. */
    String playlistName(UUID playlistId) {
      return playlist(playlistId).map(Playlist::getName).orElse("");
    }

    /** Próximo inicio de la regla en la zona dada, calculado una sola vez por regla y zona. */
    Instant nextRunAt(ScheduleRule rule, ZoneId zone) {
      return nextRunCache.computeIfAbsent(
          rule.id() + "|" + zone,
          key -> ScheduleTiming.evaluate(rule, List.of(zone), now).nextRunAt());
    }

    /** Busca una playlist con caché. */
    private java.util.Optional<Playlist> playlist(UUID playlistId) {
      return java.util.Optional.ofNullable(
          playlistCache.computeIfAbsent(playlistId, id -> playlists.findById(id).orElse(null)));
    }
  }

  /** Indica si el usuario actual puede ver la programación por alguno de sus destinos. */
  private boolean isVisible(Schedule schedule) {
    AuthenticatedUser user = access.current();
    if (user.admin()) {
      return true;
    }
    return AccessPolicy.hasAnyPermission(user, Permission.PLAYER_VIEW, Permission.SCHEDULE_MANAGE)
        && schedule.getTargets().stream()
            .anyMatch(target -> canAccessTarget(target, AccessLevel.VIEW));
  }

  /** Indica si el usuario actual alcanza un nivel sobre el destino de una programación. */
  private boolean canAccessTarget(ScheduleTarget target, AccessLevel level) {
    if (target.getPlayerId() != null) {
      return players
          .findById(target.getPlayerId())
          .map(player -> access.canAccessPlayer(player, level))
          .orElse(false);
    }
    if (target.getSiteId() != null) {
      return canAccessSiteTarget(target.getSiteId(), level);
    }
    return access.canAccessGroup(target.getGroupId(), level);
  }

  /**
   * Indica si el usuario alcanza un nivel sobre una sede como destino: para operar hace falta
   * concesión sobre la sede; para ver basta con ver alguno de sus reproductores.
   */
  private boolean canAccessSiteTarget(UUID siteId, AccessLevel level) {
    if (access.canAccessSite(siteId, level)) {
      return true;
    }
    return level == AccessLevel.VIEW
        && players.findBySiteId(siteId).stream().anyMatch(access::canViewPlayer);
  }

  /** Exige alcance de operación sobre todos los destinos actuales de la programación. */
  private void requireOperateOnExisting(Schedule schedule) {
    if (!schedule.getTargets().stream()
        .allMatch(target -> canAccessTarget(target, AccessLevel.OPERATE))) {
      throw scopeDenied();
    }
  }

  /** Exige alcance de operación sobre los destinos pedidos. */
  private void requireOperateOnRequested(ScheduleRequest.Targets targets) {
    if (targets == null) {
      return;
    }
    if (targets.playerIds() != null) {
      targets.playerIds().forEach(id -> access.requirePlayerLevel(id, AccessLevel.OPERATE));
    }
    if (targets.groupIds() != null) {
      targets.groupIds().forEach(id -> access.requireGroupLevel(id, AccessLevel.OPERATE));
    }
    if (targets.siteIds() != null) {
      targets.siteIds().forEach(id -> access.requireSiteLevel(id, AccessLevel.OPERATE));
    }
  }

  /** Construye el 403 de alcance insuficiente. */
  private ApiException scopeDenied() {
    return ApiException.forbidden(
        "SCOPE_DENIED", "No tienes acceso a todos los destinos de esta programación");
  }

  /** Busca una programación o lanza un 404. */
  private Schedule find(UUID id) {
    return schedules.findById(id).orElseThrow(() -> ApiException.notFound("Programación"));
  }

  /** Valida la petición, aplica sus datos a la programación y la guarda. */
  private ScheduleResponse save(
      Schedule schedule, ScheduleRequest request, UUID previousPlaylistId) {
    validate(request);
    requireOperateOnRequested(request.targets());
    UUID playlistId = resolvePlaylist(request);
    schedule.setName(request.name());
    schedule.setPlaylistId(playlistId);
    schedule.setPriority(request.priority() == null ? 10 : request.priority());
    schedule.setStartDate(request.startDate());
    schedule.setEndDate(request.endDate());
    schedule.setAllDay(request.allDay());
    schedule.setStartTime(request.allDay() ? null : request.startTime());
    schedule.setEndTime(request.allDay() ? null : request.endTime());
    schedule.setEnabled(request.enabled() == null || request.enabled());
    List<Integer> days =
        request.daysOfWeek() == null || request.daysOfWeek().isEmpty()
            ? List.of(1, 2, 3, 4, 5, 6, 7)
            : request.daysOfWeek();
    schedule.setDaysOfWeek(days.stream().distinct().sorted().toArray(Integer[]::new));
    schedule.getTargets().clear();
    addTargets(schedule, request.targets());
    Schedule saved = schedules.save(schedule);
    schedules.flush();
    if (previousPlaylistId != null && !previousPlaylistId.equals(playlistId)) {
      removeOrphanHidden(previousPlaylistId);
    }
    events.publishEvent(new ContentChangedEvent("schedule-save"));
    return toResponse(saved, loadZones(), clock.instant());
  }

  /** Comprueba la coherencia de la petición de programación. */
  private void validate(ScheduleRequest request) {
    if (request.playlistId() == null && request.assetId() == null) {
      throw ApiException.badRequest("PLAYLIST_REQUIRED", "Indica playlistId o assetId");
    }
    if (!request.allDay()) {
      LocalTime start = request.startTime();
      LocalTime end = request.endTime();
      if (start == null || end == null || !end.isAfter(start)) {
        throw ApiException.badRequest(
            "INVALID_TIME_RANGE", "La hora de fin debe ser posterior a la de inicio");
      }
    }
    if (request.startDate() != null
        && request.endDate() != null
        && request.endDate().isBefore(request.startDate())) {
      throw ApiException.badRequest(
          "INVALID_DATE_RANGE", "La fecha de fin es anterior a la de inicio");
    }
    if (request.daysOfWeek() != null
        && request.daysOfWeek().stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
      throw ApiException.badRequest("INVALID_DAYS", "Los días deben estar entre 1 y 7");
    }
    List<UUID> playerIds =
        request.targets().playerIds() == null ? List.of() : request.targets().playerIds();
    List<UUID> groupIds =
        request.targets().groupIds() == null ? List.of() : request.targets().groupIds();
    List<UUID> siteIds =
        request.targets().siteIds() == null ? List.of() : request.targets().siteIds();
    if (playerIds.isEmpty() && groupIds.isEmpty() && siteIds.isEmpty()) {
      throw ApiException.badRequest(
          "TARGETS_REQUIRED", "Indica al menos un reproductor, grupo o sede");
    }
  }

  /** Devuelve la playlist indicada o crea una oculta si se arrastró un medio suelto. */
  private UUID resolvePlaylist(ScheduleRequest request) {
    if (request.assetId() != null) {
      int duration =
          request.durationSec() == null ? DEFAULT_ASSET_DURATION_SEC : request.durationSec();
      return playlistService.createHiddenForAsset(request.assetId(), duration).getId();
    }
    if (!playlists.existsById(request.playlistId())) {
      throw ApiException.badRequest("PLAYLIST_NOT_FOUND", "Playlist inexistente");
    }
    return request.playlistId();
  }

  /** Sustituye los destinos de la programación por los de la petición, validando que existan. */
  private void addTargets(Schedule schedule, ScheduleRequest.Targets targets) {
    if (targets.playerIds() != null) {
      for (UUID playerId : targets.playerIds().stream().distinct().toList()) {
        if (!players.existsById(playerId)) {
          throw ApiException.badRequest("PLAYER_NOT_FOUND", "Reproductor inexistente: " + playerId);
        }
        ScheduleTarget target = newTarget(schedule);
        target.setPlayerId(playerId);
        schedule.getTargets().add(target);
      }
    }
    if (targets.groupIds() != null) {
      for (UUID groupId : targets.groupIds().stream().distinct().toList()) {
        if (!groups.existsById(groupId)) {
          throw ApiException.badRequest("GROUP_NOT_FOUND", "Grupo inexistente: " + groupId);
        }
        ScheduleTarget target = newTarget(schedule);
        target.setGroupId(groupId);
        schedule.getTargets().add(target);
      }
    }
    if (targets.siteIds() != null) {
      for (UUID siteId : targets.siteIds().stream().distinct().toList()) {
        if (!sites.existsById(siteId)) {
          throw ApiException.badRequest("SITE_NOT_FOUND", "Sede inexistente: " + siteId);
        }
        ScheduleTarget target = newTarget(schedule);
        target.setSiteId(siteId);
        schedule.getTargets().add(target);
      }
    }
  }

  /** Crea un destino vacío asociado a la programación. */
  private ScheduleTarget newTarget(Schedule schedule) {
    ScheduleTarget target = new ScheduleTarget();
    target.setId(UUID.randomUUID());
    target.setSchedule(schedule);
    return target;
  }

  /** Borra una playlist oculta que ya no usa ninguna programación. */
  private void removeOrphanHidden(UUID playlistId) {
    playlists
        .findById(playlistId)
        .filter(Playlist::isHidden)
        .filter(playlist -> schedules.countByPlaylistId(playlistId) == 0)
        .ifPresent(playlistService::deleteHidden);
  }

  /** Convierte una programación en su DTO. */
  private ScheduleResponse toResponse(Schedule schedule, ScheduleZones zones, Instant now) {
    List<String> zoneNames = zones.zonesOf(schedule);
    ScheduleTiming.Timing timing =
        ScheduleTiming.evaluate(
            ScheduleRule.from(schedule),
            zoneNames.stream().map(PlayerTimezoneResolver::zoneOf).toList(),
            now);
    Playlist playlist = playlists.findById(schedule.getPlaylistId()).orElse(null);
    PlaylistService.Summary summary = summary(playlist);
    boolean admin = access.current().admin();
    List<ScheduleTarget> shown =
        schedule.getTargets().stream()
            .filter(target -> admin || canAccessTarget(target, AccessLevel.VIEW))
            .toList();
    List<UUID> playerIds =
        shown.stream().map(ScheduleTarget::getPlayerId).filter(java.util.Objects::nonNull).toList();
    List<UUID> groupIds =
        shown.stream().map(ScheduleTarget::getGroupId).filter(java.util.Objects::nonNull).toList();
    List<UUID> siteIds =
        shown.stream().map(ScheduleTarget::getSiteId).filter(java.util.Objects::nonNull).toList();
    return new ScheduleResponse(
        schedule.getId(),
        schedule.getName(),
        schedule.getPlaylistId(),
        playlist == null ? null : playlist.getName(),
        playlist != null && playlist.isHidden(),
        schedule.getPriority(),
        schedule.getStartDate(),
        schedule.getEndDate(),
        schedule.getStartTime(),
        schedule.getEndTime(),
        List.of(schedule.getDaysOfWeek()),
        schedule.isAllDay(),
        schedule.isEnabled(),
        new ScheduleRequest.Targets(playerIds, groupIds, siteIds),
        summary.thumbnailUrls(),
        summary.itemCount(),
        summary.totalDurationSec(),
        timing.status(),
        timing.nextRunAt(),
        zoneNames.size() == 1 ? zoneNames.get(0) : null,
        zoneNames);
  }

  /** Resume la playlist de una programación; vacío si ya no existe. */
  private static PlaylistService.Summary summary(Playlist playlist) {
    return playlist == null
        ? new PlaylistService.Summary(List.of(), 0, 0)
        : PlaylistService.summarize(playlist);
  }
}
