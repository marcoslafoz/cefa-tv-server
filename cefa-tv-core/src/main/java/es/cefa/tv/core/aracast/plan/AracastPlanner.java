package es.cefa.tv.core.aracast.plan;

import es.cefa.tv.core.aracast.template.MediaNaming;
import es.cefa.tv.core.manifest.model.ManifestDocument;
import es.cefa.tv.core.manifest.model.ManifestDocument.AssetEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.FileEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.ItemEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.PageEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.PlaylistEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.ScheduleEntry;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Traduce el manifest de un reproductor a lo que un Aracast puede emitir: plantillas y una
 * programación semanal de eventos.
 *
 * <p>Un Aracast no tiene prioridades ni rangos de fechas, así que se resuelven aquí con la misma
 * regla que el reproductor de CEFA TV: gana la programación activa de mayor prioridad y, en empate,
 * la que empezó más tarde. Se calcula cada uno de los próximos siete días y los eventos iguales se
 * agrupan por día de la semana; al regenerarse a diario el resultado es exacto para esa semana y,
 * si el servidor dejara de publicar, el player seguiría repitiendo el último patrón.
 */
public final class AracastPlanner {

  /** Días que se calculan por delante. */
  public static final int WINDOW_DAYS = 7;

  /** Máximo de eventos en una programación; un fichero mayor no es razonable para el player. */
  public static final int MAX_EVENTS = 3000;

  private static final int SECONDS_PER_DAY = 24 * 60 * 60;
  private static final int LAST_SECOND = SECONDS_PER_DAY - 30;
  private static final int NAME_LIMIT = 50;

  /** Plantilla que el player emite cuando no hay ningún evento activo. */
  public static final String DEFAULT_TEMPLATE = "basic_json";

  private AracastPlanner() {}

  /**
   * Calcula el plan de un manifest.
   *
   * @param manifest manifest del reproductor
   * @param today primer día de la ventana, en la zona horaria del reproductor
   * @return plantillas y eventos
   * @throws UnsupportedPlaylistException si una playlist no se puede emitir
   */
  public static AracastPlan plan(ManifestDocument manifest, LocalDate today) {
    Map<String, PlaylistPlan> playlists = new LinkedHashMap<>();
    for (PlaylistEntry playlist : nullSafe(manifest.playlists()).values()) {
      playlists.put(playlist.id(), planPlaylist(playlist, manifest));
    }
    Map<EventKey, Set<DayOfWeek>> grouped = new LinkedHashMap<>();
    for (int offset = 0; offset < WINDOW_DAYS; offset++) {
      LocalDate date = today.plusDays(offset);
      for (Segment segment : daySegments(manifest, date)) {
        PlaylistPlan plan = playlists.get(segment.playlistId());
        if (plan != null) {
          for (EventKey event : expand(plan, segment)) {
            grouped
                .computeIfAbsent(event, key -> EnumSet.noneOf(DayOfWeek.class))
                .add(date.getDayOfWeek());
          }
        }
      }
    }
    if (grouped.size() > MAX_EVENTS) {
      throw new UnsupportedPlaylistException(
          "La programación necesita "
              + grouped.size()
              + " cambios de plantilla (máximo "
              + MAX_EVENTS
              + "). Usa elementos más largos o menos elementos mezclados en las playlists");
    }
    List<PlannedEvent> events =
        grouped.entrySet().stream()
            .map(
                e ->
                    new PlannedEvent(
                        e.getKey().template(),
                        LocalTime.ofSecondOfDay(e.getKey().start()),
                        LocalTime.ofSecondOfDay(e.getKey().end()),
                        e.getValue()))
            .sorted(
                Comparator.comparing(PlannedEvent::start)
                    .thenComparing(PlannedEvent::end)
                    .thenComparing(PlannedEvent::template))
            .toList();
    Map<String, PlannedTemplate> templates = new LinkedHashMap<>();
    playlists.values().forEach(p -> p.templates().forEach(t -> templates.putIfAbsent(t.name(), t)));
    Set<String> used = new TreeSet<>();
    events.forEach(e -> used.add(e.template()));
    return new AracastPlan(
        templates.values().stream().filter(t -> used.contains(t.name())).toList(), events);
  }

  private static PlaylistPlan planPlaylist(PlaylistEntry playlist, ManifestDocument manifest) {
    List<Resolved> items = new ArrayList<>();
    for (ItemEntry item : nullSafe(playlist.items())) {
      AssetEntry asset = nullSafe(manifest.assets()).get(item.assetId());
      if (asset == null) {
        throw unsupported(playlist, "usa un medio que ya no existe");
      }
      items.add(new Resolved(item, asset));
    }
    if (items.isEmpty()) {
      throw unsupported(playlist, "no tiene elementos");
    }
    Map<String, FileEntry> files = filesBySha(manifest);
    if (items.stream().allMatch(Resolved::isVisual)) {
      List<PlannedContent> images = new ArrayList<>();
      List<Integer> seconds = new ArrayList<>();
      for (Resolved r : items) {
        for (PlannedContent image : r.images(files, playlist)) {
          images.add(image);
          seconds.add(r.item().durationSec());
        }
      }
      if (seconds.stream().distinct().count() == 1) {
        int each = seconds.get(0);
        PlannedTemplate template =
            images.size() == 1
                ? new PlannedTemplate(
                    templateName(playlist, null), TemplateKind.IMAGE, images, each, null, true)
                : new PlannedTemplate(
                    templateName(playlist, null), TemplateKind.SLIDESHOW, images, each, null, true);
        return PlaylistPlan.single(template);
      }
    } else if (items.stream().allMatch(r -> "VIDEO".equals(r.asset().type()))
        && items.stream().map(r -> r.item().muted()).distinct().count() == 1) {
      List<PlannedContent> videos = new ArrayList<>();
      for (Resolved r : items) {
        videos.add(fileOf(r.asset().sha256(), files, playlist));
      }
      return PlaylistPlan.single(
          new PlannedTemplate(
              templateName(playlist, null),
              TemplateKind.VIDEOS,
              videos,
              0,
              null,
              items.get(0).item().muted()));
    } else if (items.size() == 1) {
      return PlaylistPlan.single(itemTemplate(playlist, items.get(0), files, null));
    }
    return sequence(playlist, items, files);
  }

  private static PlaylistPlan sequence(
      PlaylistEntry playlist, List<Resolved> items, Map<String, FileEntry> files) {
    List<PlannedTemplate> templates = new ArrayList<>();
    List<Step> steps = new ArrayList<>();
    int number = 1;
    for (Resolved r : items) {
      PlannedTemplate template = itemTemplate(playlist, r, files, number++);
      int seconds = r.stepSeconds(playlist);
      templates.add(template);
      steps.add(new Step(template.name(), seconds));
    }
    return new PlaylistPlan(steps, templates, false);
  }

  private static PlannedTemplate itemTemplate(
      PlaylistEntry playlist, Resolved r, Map<String, FileEntry> files, Integer number) {
    String name = templateName(playlist, number);
    AssetEntry asset = r.asset();
    return switch (asset.type()) {
      case "IMAGE" ->
          new PlannedTemplate(
              name,
              TemplateKind.IMAGE,
              List.of(fileOf(asset.sha256(), files, playlist)),
              r.item().durationSec(),
              null,
              true);
      case "PDF" -> {
        List<PlannedContent> pages = r.images(files, playlist);
        yield new PlannedTemplate(
            name,
            pages.size() == 1 ? TemplateKind.IMAGE : TemplateKind.SLIDESHOW,
            pages,
            r.item().durationSec(),
            null,
            true);
      }
      case "VIDEO" ->
          new PlannedTemplate(
              name,
              TemplateKind.VIDEOS,
              List.of(fileOf(asset.sha256(), files, playlist)),
              0,
              null,
              r.item().muted());
      case "WEB" -> {
        if (asset.url() == null || asset.url().isBlank()) {
          throw unsupported(playlist, "tiene una página web sin dirección");
        }
        yield new PlannedTemplate(name, TemplateKind.WEB, List.of(), 0, asset.url(), true);
      }
      case "HTML" ->
          new PlannedTemplate(
              name,
              TemplateKind.HTML,
              List.of(fileOf(asset.sha256(), files, playlist)),
              0,
              null,
              true);
      default -> throw unsupported(playlist, "usa un tipo de medio no admitido: " + asset.type());
    };
  }

  static String templateName(PlaylistEntry playlist, Integer number) {
    String base = MediaNaming.safe(playlist.name() == null ? "Playlist" : playlist.name());
    if (base.isEmpty()) {
      base = "Playlist";
    }
    if (base.length() > NAME_LIMIT) {
      base = base.substring(0, NAME_LIMIT).trim();
    }
    String id = playlist.id().replace("-", "");
    String marker = "~ctv-" + id.substring(0, 8) + (number == null ? "" : "-" + number);
    return base + (number == null ? "" : " " + number) + " " + marker;
  }

  /**
   * Plantilla que debe estar en emisión en un instante según el plan.
   *
   * @param plan plan publicado
   * @param now instante en la zona horaria del player
   * @return el nombre de la plantilla, o {@code basic_json} (el logo) si no hay evento activo
   */
  public static String activeTemplate(AracastPlan plan, java.time.LocalDateTime now) {
    LocalTime time = now.toLocalTime();
    return plan.events().stream()
        .filter(e -> e.days().contains(now.getDayOfWeek()))
        .filter(e -> !time.isBefore(e.start()) && time.isBefore(e.end()))
        .map(PlannedEvent::template)
        .findFirst()
        .orElse(DEFAULT_TEMPLATE);
  }

  private static List<Segment> daySegments(ManifestDocument manifest, LocalDate date) {
    List<Sched> schedules =
        nullSafe(manifest.schedules()).stream()
            .filter(ScheduleEntry::enabled)
            .map(Sched::of)
            .toList();
    TreeSet<Integer> boundaries = new TreeSet<>();
    boundaries.add(0);
    for (Sched s : schedules) {
      boundaries.add(s.start());
      if (s.end() != null) {
        boundaries.add(s.end());
      }
    }
    boundaries.removeIf(b -> b >= SECONDS_PER_DAY);
    List<Integer> points = new ArrayList<>(boundaries);
    List<Segment> segments = new ArrayList<>();
    for (int i = 0; i < points.size(); i++) {
      int from = points.get(i);
      int to = i + 1 < points.size() ? points.get(i + 1) : SECONDS_PER_DAY;
      String playlistId =
          winner(schedules, date, from)
              .map(Sched::playlistId)
              .orElse(
                  manifest.fallbackPlaylistId() == null
                      ? null
                      : manifest.fallbackPlaylistId().toString());
      if (playlistId == null) {
        continue;
      }
      Segment last = segments.isEmpty() ? null : segments.get(segments.size() - 1);
      if (last != null && last.end() == from && last.playlistId().equals(playlistId)) {
        segments.set(segments.size() - 1, new Segment(last.start(), to, playlistId));
      } else {
        segments.add(new Segment(from, to, playlistId));
      }
    }
    return segments;
  }

  private static Optional<Sched> winner(List<Sched> schedules, LocalDate date, int second) {
    return schedules.stream()
        .filter(s -> s.isActive(date, second))
        .reduce((a, b) -> Sched.PRECEDENCE.compare(a, b) >= 0 ? a : b);
  }

  private static List<EventKey> expand(PlaylistPlan plan, Segment segment) {
    List<EventKey> events = new ArrayList<>();
    if (plan.single()) {
      add(events, plan.steps().get(0).template(), segment.start(), segment.end());
      return events;
    }
    int t = segment.start();
    while (t < segment.end()) {
      for (Step step : plan.steps()) {
        int end = Math.min(t + step.seconds(), segment.end());
        add(events, step.template(), t, end);
        t = end;
        if (t >= segment.end()) {
          break;
        }
      }
    }
    return events;
  }

  private static void add(List<EventKey> events, String template, int start, int end) {
    int cappedEnd = Math.min(end, LAST_SECOND);
    if (cappedEnd > start) {
      events.add(new EventKey(template, start, cappedEnd));
    }
  }

  private static Map<String, FileEntry> filesBySha(ManifestDocument manifest) {
    Map<String, FileEntry> files = new LinkedHashMap<>();
    nullSafe(manifest.files()).forEach(f -> files.putIfAbsent(f.sha256(), f));
    return files;
  }

  private static PlannedContent fileOf(
      String sha256, Map<String, FileEntry> files, PlaylistEntry playlist) {
    FileEntry file = sha256 == null ? null : files.get(sha256);
    if (file == null) {
      throw unsupported(playlist, "usa un fichero que no está en el manifest");
    }
    return new PlannedContent(file.sha256(), file.name(), file.mime());
  }

  private static UnsupportedPlaylistException unsupported(PlaylistEntry playlist, String reason) {
    return new UnsupportedPlaylistException(
        "La playlist \"" + playlist.name() + "\" no se puede emitir en un Aracast: " + reason);
  }

  private static <T> List<T> nullSafe(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static <K, V> Map<K, V> nullSafe(Map<K, V> map) {
    return map == null ? Map.of() : map;
  }

  private record Segment(int start, int end, String playlistId) {}

  private record EventKey(String template, int start, int end) {}

  private record Step(String template, int seconds) {}

  private record PlaylistPlan(List<Step> steps, List<PlannedTemplate> templates, boolean single) {

    static PlaylistPlan single(PlannedTemplate template) {
      return new PlaylistPlan(List.of(new Step(template.name(), 0)), List.of(template), true);
    }
  }

  private record Resolved(ItemEntry item, AssetEntry asset) {

    boolean isVisual() {
      return "IMAGE".equals(asset.type()) || "PDF".equals(asset.type());
    }

    List<PlannedContent> images(Map<String, FileEntry> files, PlaylistEntry playlist) {
      if ("IMAGE".equals(asset.type())) {
        return List.of(fileOf(asset.sha256(), files, playlist));
      }
      List<PageEntry> pages = asset.pages() == null ? List.of() : asset.pages();
      if (pages.isEmpty()) {
        throw unsupported(playlist, "tiene un PDF sin páginas");
      }
      return pages.stream()
          .sorted(Comparator.comparingInt(PageEntry::index))
          .map(page -> fileOf(page.sha256(), files, playlist))
          .toList();
    }

    int stepSeconds(PlaylistEntry playlist) {
      int seconds =
          switch (asset.type()) {
            case "PDF" ->
                item.durationSec() * Math.max(1, asset.pages() == null ? 1 : asset.pages().size());
            case "VIDEO" ->
                asset.durationMs() != null && asset.durationMs() > 0
                    ? (int) Math.ceil(asset.durationMs() / 1000.0)
                    : item.durationSec();
            default -> item.durationSec();
          };
      if (seconds < 1) {
        throw unsupported(playlist, "tiene un elemento sin duración");
      }
      return seconds;
    }
  }

  private record Sched(
      ScheduleEntry entry,
      LocalDate startDate,
      LocalDate endDate,
      int start,
      Integer end,
      Set<Integer> days) {

    static final Comparator<Sched> PRECEDENCE =
        Comparator.comparingInt((Sched s) -> s.entry().priority())
            .thenComparing(Sched::startDate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingInt(Sched::start);

    static Sched of(ScheduleEntry entry) {
      boolean allDay = entry.allDay();
      int start = allDay || entry.startTime() == null ? 0 : seconds(entry.startTime());
      Integer end = allDay || entry.endTime() == null ? null : seconds(entry.endTime());
      return new Sched(
          entry,
          entry.startDate() == null ? null : LocalDate.parse(entry.startDate()),
          entry.endDate() == null ? null : LocalDate.parse(entry.endDate()),
          start,
          end,
          entry.daysOfWeek() == null ? Set.of() : Set.copyOf(entry.daysOfWeek()));
    }

    String playlistId() {
      return entry.playlistId();
    }

    boolean isActive(LocalDate date, int second) {
      if (entry.allDay()) {
        return matchesDay(date);
      }
      if (end == null) {
        return matchesDay(date) && second >= start;
      }
      if (end > start) {
        return matchesDay(date) && second >= start && second < end;
      }
      boolean startsToday = matchesDay(date) && second >= start;
      boolean spillsFromYesterday = matchesDay(date.minusDays(1)) && second < end;
      return startsToday || spillsFromYesterday;
    }

    private boolean matchesDay(LocalDate date) {
      if (startDate != null && date.isBefore(startDate)) {
        return false;
      }
      if (endDate != null && date.isAfter(endDate)) {
        return false;
      }
      return days.isEmpty() || days.contains(date.getDayOfWeek().getValue());
    }

    private static int seconds(String time) {
      return Objects.requireNonNull(LocalTime.parse(time)).toSecondOfDay();
    }
  }
}
