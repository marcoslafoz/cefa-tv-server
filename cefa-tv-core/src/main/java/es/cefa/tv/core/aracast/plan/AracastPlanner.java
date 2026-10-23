package es.cefa.tv.core.aracast.plan;

import es.cefa.tv.core.aracast.template.MediaNaming;
import es.cefa.tv.core.manifest.model.ManifestDocument;
import es.cefa.tv.core.manifest.model.ManifestDocument.AssetEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.FileEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.ItemEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.PageEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.PlaylistEntry;
import es.cefa.tv.core.manifest.model.ManifestDocument.ScheduleEntry;
import es.cefa.tv.core.player.model.IdleLogo;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
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

  /** Minutos por delante que cubren los eventos con fecha de los ciclos de plantillas. */
  public static final int ROLLING_MINUTES = 180;

  private static final int MIN_ROLLING_MINUTES = 20;
  private static final int SECONDS_PER_DAY = 24 * 60 * 60;
  private static final int LAST_SECOND = SECONDS_PER_DAY - 30;
  private static final int NAME_LIMIT = 50;
  private static final int MAX_SLIDES = 240;

  /** Plantilla que el propio player emite cuando no hay ningún evento ni plantilla por defecto. */
  public static final String DEFAULT_TEMPLATE = "basic_json";

  private AracastPlanner() {}

  /**
   * Calcula el plan de un manifest.
   *
   * <p>Una playlist que cabe en una sola plantilla se publica como eventos semanales para los
   * próximos siete días. Una que necesita un ciclo de plantillas se publica como eventos con fecha
   * solo para las próximas horas, porque repetidos todo el día serían miles; se regeneran a menudo.
   *
   * @param manifest manifest del reproductor
   * @param now instante actual en la zona horaria del reproductor
   * @param idle logo de la pantalla en reposo
   * @return plantillas y eventos
   * @throws UnsupportedPlaylistException si una playlist de una programación activa no se puede
   *     emitir; las playlists que ninguna programación usa y una de reposo no emitible se ignoran
   */
  public static AracastPlan plan(ManifestDocument manifest, LocalDateTime now, IdleLogo idle) {
    LocalDate today = now.toLocalDate();
    Map<String, PlaylistPlan> playlists = new LinkedHashMap<>();
    Set<String> scheduled = new HashSet<>();
    nullSafe(manifest.schedules()).stream()
        .filter(ScheduleEntry::enabled)
        .forEach(s -> scheduled.add(s.playlistId()));
    String fallbackId =
        manifest.fallbackPlaylistId() == null ? null : manifest.fallbackPlaylistId().toString();
    for (PlaylistEntry playlist : nullSafe(manifest.playlists()).values()) {
      if (scheduled.contains(playlist.id())) {
        playlists.put(playlist.id(), planPlaylist(playlist, manifest));
      } else if (playlist.id().equals(fallbackId)) {
        try {
          playlists.put(playlist.id(), planPlaylist(playlist, manifest));
        } catch (UnsupportedPlaylistException e) {
          // La playlist de reposo no usable no debe impedir publicar la programación: se muestra el
          // logo de reposo.
        }
      }
    }
    Map<EventKey, Set<DayOfWeek>> weekly = new LinkedHashMap<>();
    List<Segment> cycles = new ArrayList<>();
    for (int offset = 0; offset < WINDOW_DAYS; offset++) {
      LocalDate date = today.plusDays(offset);
      for (Segment segment : daySegments(manifest, date)) {
        PlaylistPlan plan = playlists.get(segment.playlistId());
        if (plan == null) {
          continue;
        }
        if (plan.single()) {
          add(weekly, date, plan.steps().get(0).template(), segment.start(), segment.end());
        } else {
          cycles.add(segment.on(date));
        }
      }
    }
    LocalDateTime windowStart = now.truncatedTo(ChronoUnit.MINUTES);
    List<PlannedEvent> dated = List.of();
    for (int minutes = ROLLING_MINUTES; ; minutes /= 2) {
      dated = expandCycles(cycles, playlists, windowStart, windowStart.plusMinutes(minutes));
      if (weekly.size() + dated.size() <= MAX_EVENTS) {
        break;
      }
      if (minutes / 2 < MIN_ROLLING_MINUTES) {
        throw new UnsupportedPlaylistException(
            "La programación necesita demasiados cambios de plantilla ("
                + (weekly.size() + dated.size())
                + ", máximo "
                + MAX_EVENTS
                + "). Usa elementos más largos o menos elementos mezclados en las playlists");
      }
    }
    List<PlannedEvent> events = new ArrayList<>();
    weekly.entrySet().stream()
        .map(
            e ->
                new PlannedEvent(
                    e.getKey().template(),
                    LocalTime.ofSecondOfDay(e.getKey().start()),
                    LocalTime.ofSecondOfDay(e.getKey().end()),
                    e.getValue(),
                    null))
        .sorted(
            Comparator.comparing(PlannedEvent::start)
                .thenComparing(PlannedEvent::end)
                .thenComparing(PlannedEvent::template))
        .forEach(events::add);
    events.addAll(dated);
    Map<String, PlannedTemplate> templates = new LinkedHashMap<>();
    playlists.values().forEach(p -> p.templates().forEach(t -> templates.putIfAbsent(t.name(), t)));
    Set<String> used = new TreeSet<>();
    events.forEach(e -> used.add(e.template()));
    return new AracastPlan(
        templates.values().stream().filter(t -> used.contains(t.name())).toList(),
        events,
        idleTemplate(idle),
        !cycles.isEmpty());
  }

  /**
   * Plantilla que debe estar en emisión en un instante según el plan.
   *
   * @param plan plan publicado
   * @param now instante en la zona horaria del player
   * @return el nombre de la plantilla; la de reposo si no hay evento activo
   */
  public static String activeTemplate(AracastPlan plan, LocalDateTime now) {
    LocalTime time = now.toLocalTime();
    return plan.events().stream()
        .filter(
            e ->
                e.date() == null
                    ? e.days().contains(now.getDayOfWeek())
                    : e.date().equals(now.toLocalDate()))
        .filter(e -> !time.isBefore(e.start()) && time.isBefore(e.end()))
        .map(PlannedEvent::template)
        .findFirst()
        .orElse(plan.idle() == null ? DEFAULT_TEMPLATE : plan.idle().name());
  }

  /**
   * Plantilla de la pantalla en reposo: el logo sobre fondo oscuro, igual que en un reproductor.
   *
   * @param idle logo elegido
   * @return la plantilla, con la imagen incluida en la aplicación
   */
  public static PlannedTemplate idleTemplate(IdleLogo idle) {
    IdleLogo logo = idle == null ? IdleLogo.CEFA : idle;
    boolean cefa = logo == IdleLogo.CEFA;
    return new PlannedTemplate(
        cefa
            ? "Pantalla en reposo CEFA ~ctv-0000cefa"
            : "Pantalla en reposo ALGONTEC ~ctv-0a190000",
        TemplateKind.IMAGE,
        List.of(
            new PlannedContent(
                BUILTIN + (cefa ? "idle-cefa" : "idle-algontec"),
                cefa ? "pantalla en reposo cefa.png" : "pantalla en reposo algontec.png",
                "image/png")),
        10,
        null,
        true);
  }

  /** Prefijo del identificador de un fichero que viene incluido en la aplicación. */
  public static final String BUILTIN = "builtin:";

  private static void add(
      Map<EventKey, Set<DayOfWeek>> weekly, LocalDate date, String template, int start, int end) {
    int cappedEnd = Math.min(end, LAST_SECOND);
    if (cappedEnd > start) {
      weekly
          .computeIfAbsent(
              new EventKey(template, start, cappedEnd), key -> EnumSet.noneOf(DayOfWeek.class))
          .add(date.getDayOfWeek());
    }
  }

  /** Expande los ciclos en eventos con fecha dentro de la ventana de tiempo. */
  private static List<PlannedEvent> expandCycles(
      List<Segment> cycles,
      Map<String, PlaylistPlan> playlists,
      LocalDateTime windowStart,
      LocalDateTime windowEnd) {
    List<PlannedEvent> events = new ArrayList<>();
    for (Segment segment : cycles) {
      PlaylistPlan plan = playlists.get(segment.playlistId());
      LocalDateTime segmentStart = segment.date().atStartOfDay().plusSeconds(segment.start());
      LocalDateTime segmentEnd =
          segment.date().atStartOfDay().plusSeconds(Math.min(segment.end(), LAST_SECOND));
      LocalDateTime from = segmentStart.isAfter(windowStart) ? segmentStart : windowStart;
      LocalDateTime to = segmentEnd.isBefore(windowEnd) ? segmentEnd : windowEnd;
      if (!from.isBefore(to)) {
        continue;
      }
      LocalDateTime cursor = segmentStart;
      while (cursor.isBefore(to)) {
        for (Step step : plan.steps()) {
          LocalDateTime stepEnd = cursor.plusSeconds(step.seconds());
          LocalDateTime start = cursor.isAfter(from) ? cursor : from;
          LocalDateTime end = stepEnd.isBefore(to) ? stepEnd : to;
          if (end.isAfter(start)) {
            events.add(
                new PlannedEvent(
                    step.template(),
                    start.toLocalTime(),
                    end.toLocalTime(),
                    Set.of(),
                    start.toLocalDate()));
          }
          cursor = stepEnd;
          if (!cursor.isBefore(to)) {
            break;
          }
        }
      }
    }
    events.sort(
        Comparator.comparing(PlannedEvent::date)
            .thenComparing(PlannedEvent::start)
            .thenComparing(PlannedEvent::template));
    return events;
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
      PlannedTemplate repeated = repeatedSlides(playlist, images, seconds);
      if (repeated != null) {
        return PlaylistPlan.single(repeated);
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

  /**
   * Pase de diapositivas para imágenes con duraciones distintas: el player solo admite una duración
   * por diapositiva, así que cada imagen se repite tantas veces como duraciones base contiene (el
   * máximo común divisor). La transición entre dos copias iguales no se nota.
   */
  private static PlannedTemplate repeatedSlides(
      PlaylistEntry playlist, List<PlannedContent> images, List<Integer> seconds) {
    int divisor = 0;
    for (int value : seconds) {
      divisor = gcd(divisor, value);
    }
    if (divisor < 1) {
      return null;
    }
    List<PlannedContent> slides = new ArrayList<>();
    for (int i = 0; i < images.size(); i++) {
      for (int copy = 0; copy < seconds.get(i) / divisor; copy++) {
        slides.add(images.get(i));
      }
    }
    if (slides.size() > MAX_SLIDES) {
      return null;
    }
    return new PlannedTemplate(
        templateName(playlist, null), TemplateKind.SLIDESHOW, slides, divisor, null, true);
  }

  private static int gcd(int a, int b) {
    return b == 0 ? a : gcd(b, a % b);
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

  private record Segment(int start, int end, String playlistId, LocalDate date) {

    Segment(int start, int end, String playlistId) {
      this(start, end, playlistId, null);
    }

    Segment on(LocalDate day) {
      return new Segment(start, end, playlistId, day);
    }
  }

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
