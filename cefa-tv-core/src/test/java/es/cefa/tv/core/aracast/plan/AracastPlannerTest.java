package es.cefa.tv.core.aracast.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AracastPlannerTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
  private static final java.time.LocalDateTime NOW = MONDAY.atTime(8, 0);
  private static final String P1 = "11111111-1111-1111-1111-111111111111";
  private static final String P2 = "22222222-2222-2222-2222-222222222222";
  private static final String P3 = "33333333-3333-3333-3333-333333333333";

  private final Map<String, AssetEntry> assets = new LinkedHashMap<>();
  private final Map<String, PlaylistEntry> playlists = new LinkedHashMap<>();
  private final List<FileEntry> files = new ArrayList<>();
  private final List<ScheduleEntry> schedules = new ArrayList<>();
  private String fallback;

  private void image(String id) {
    assets.put(
        id, new AssetEntry(id, "IMAGE", id, "sha-" + id, "image/png", null, null, null, null));
    files.add(new FileEntry("sha-" + id, 10, "image/png", id + ".png"));
  }

  private void video(String id, long durationMs) {
    assets.put(
        id,
        new AssetEntry(id, "VIDEO", id, "sha-" + id, "video/mp4", durationMs, null, null, null));
    files.add(new FileEntry("sha-" + id, 10, "video/mp4", id + ".mp4"));
  }

  private void web(String id, String url) {
    assets.put(id, new AssetEntry(id, "WEB", id, null, null, null, url, 60, null));
  }

  private void pdf(String id, int pages) {
    List<PageEntry> list = new ArrayList<>();
    for (int i = 0; i < pages; i++) {
      list.add(new PageEntry(i, "sha-" + id + "-" + i));
      files.add(
          new FileEntry("sha-" + id + "-" + i, 10, "image/jpeg", id + "-p" + (i + 1) + ".jpg"));
    }
    assets.put(id, new AssetEntry(id, "PDF", id, null, "application/pdf", null, null, null, list));
  }

  private void playlist(String id, String name, ItemEntry... items) {
    playlists.put(id, new PlaylistEntry(id, name, List.of(items)));
  }

  private static ItemEntry item(String asset, int seconds) {
    return new ItemEntry(asset, seconds, "CONTAIN", false);
  }

  private void schedule(
      String playlist,
      int priority,
      String startDate,
      String endDate,
      String start,
      String end,
      List<Integer> days,
      boolean allDay) {
    schedules.add(
        new ScheduleEntry(
            UUID.randomUUID().toString(),
            playlist,
            priority,
            startDate,
            endDate,
            start,
            end,
            days,
            allDay,
            true));
  }

  private AracastPlan plan() {
    return AracastPlanner.plan(
        new ManifestDocument(
            1,
            null,
            UUID.randomUUID(),
            "Europe/Madrid",
            fallback == null ? null : UUID.fromString(fallback),
            files,
            assets,
            playlists,
            schedules),
        NOW,
        IdleLogo.CEFA);
  }

  private static PlannedEvent event(PlannedEventSpec spec, AracastPlan plan) {
    return plan.events().stream()
        .filter(e -> e.template().startsWith(spec.template()) && e.start().equals(spec.start()))
        .findFirst()
        .orElseThrow();
  }

  private record PlannedEventSpec(String template, LocalTime start) {}

  @Test
  void anAllDayScheduleBecomesOneEventForEveryDay() {
    image("a");
    playlist(P1, "Oficinas", item("a", 10));
    schedule(P1, 10, null, null, null, null, List.of(), true);

    AracastPlan plan = plan();

    assertThat(plan.templates()).hasSize(1);
    assertThat(plan.templates().get(0).name()).isEqualTo("Oficinas ~ctv-11111111");
    assertThat(plan.templates().get(0).kind()).isEqualTo(TemplateKind.IMAGE);
    assertThat(plan.events()).hasSize(1);
    PlannedEvent event = plan.events().get(0);
    assertThat(event.start()).isEqualTo(LocalTime.MIDNIGHT);
    assertThat(event.end()).isEqualTo(LocalTime.of(23, 59, 30));
    assertThat(event.days()).containsExactlyInAnyOrder(DayOfWeek.values());
  }

  @Test
  void aHigherPriorityScheduleOverridesTheLowerOneInItsWindow() {
    image("a");
    image("b");
    playlist(P1, "Base", item("a", 10));
    playlist(P2, "Aviso", item("b", 10));
    schedule(P1, 10, null, null, null, null, List.of(), true);
    schedule(P2, 50, null, null, "10:00", "12:00", List.of(), false);

    AracastPlan plan = plan();

    assertThat(plan.events())
        .extracting(PlannedEvent::template, PlannedEvent::start, PlannedEvent::end)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "Base ~ctv-11111111", LocalTime.MIDNIGHT, LocalTime.of(10, 0)),
            org.assertj.core.groups.Tuple.tuple(
                "Aviso ~ctv-22222222", LocalTime.of(10, 0), LocalTime.of(12, 0)),
            org.assertj.core.groups.Tuple.tuple(
                "Base ~ctv-11111111", LocalTime.of(12, 0), LocalTime.of(23, 59, 30)));
  }

  @Test
  void onEqualPriorityTheScheduleThatStartedLaterWins() {
    image("a");
    image("b");
    playlist(P1, "Primera", item("a", 10));
    playlist(P2, "Segunda", item("b", 10));
    schedule(P1, 10, null, null, "08:00", "18:00", List.of(), false);
    schedule(P2, 10, null, null, "12:00", "14:00", List.of(), false);

    AracastPlan plan = plan();

    assertThat(plan.events())
        .extracting(PlannedEvent::template)
        .containsExactly("Primera ~ctv-11111111", "Segunda ~ctv-22222222", "Primera ~ctv-11111111");
  }

  @Test
  void anOvernightScheduleSpillsIntoTheNextMorning() {
    image("a");
    playlist(P1, "Noche", item("a", 10));
    schedule(P1, 10, null, null, "22:00", "06:00", List.of(1), false);

    AracastPlan plan = plan();

    PlannedEvent morning = event(new PlannedEventSpec("Noche", LocalTime.MIDNIGHT), plan);
    assertThat(morning.end()).isEqualTo(LocalTime.of(6, 0));
    assertThat(morning.days()).containsExactly(DayOfWeek.TUESDAY);
    PlannedEvent evening = event(new PlannedEventSpec("Noche", LocalTime.of(22, 0)), plan);
    assertThat(evening.end()).isEqualTo(LocalTime.of(23, 59, 30));
    assertThat(evening.days()).containsExactly(DayOfWeek.MONDAY);
  }

  @Test
  void theFallbackPlaylistFillsTheGaps() {
    image("a");
    image("b");
    playlist(P1, "Horario", item("a", 10));
    playlist(P3, "Reposo", item("b", 10));
    fallback = P3;
    schedule(P1, 10, null, null, "09:00", "17:00", List.of(1, 2, 3, 4, 5), false);

    AracastPlan plan = plan();

    assertThat(plan.events())
        .extracting(PlannedEvent::template, PlannedEvent::start, PlannedEvent::end)
        .contains(
            org.assertj.core.groups.Tuple.tuple(
                "Horario ~ctv-11111111", LocalTime.of(9, 0), LocalTime.of(17, 0)),
            org.assertj.core.groups.Tuple.tuple(
                "Reposo ~ctv-33333333", LocalTime.MIDNIGHT, LocalTime.of(9, 0)),
            org.assertj.core.groups.Tuple.tuple(
                "Reposo ~ctv-33333333", LocalTime.MIDNIGHT, LocalTime.of(23, 59, 30)));
  }

  @Test
  void aDateRangeOnlyAppliesToTheDaysInsideTheWindow() {
    image("a");
    playlist(P1, "Campaña", item("a", 10));
    schedule(P1, 10, "2026-10-21", "2026-10-22", null, null, List.of(), true);

    AracastPlan plan = plan();

    assertThat(plan.events()).hasSize(1);
    assertThat(plan.events().get(0).days())
        .containsExactlyInAnyOrder(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
  }

  @Test
  void aSlideshowKeepsAllImagesWhenTheyShareTheDuration() {
    image("a");
    image("b");
    pdf("d", 2);
    playlist(P1, "Pase", item("a", 8), item("b", 8), item("d", 8));
    schedule(P1, 10, null, null, null, null, List.of(), true);

    PlannedTemplate template = plan().templates().get(0);

    assertThat(template.kind()).isEqualTo(TemplateKind.SLIDESHOW);
    assertThat(template.secondsPerSlide()).isEqualTo(8);
    assertThat(template.contents())
        .extracting(PlannedContent::sha256)
        .containsExactly("sha-a", "sha-b", "sha-d-0", "sha-d-1");
  }

  @Test
  void videosWithTheSameSoundSettingShareOneTemplate() {
    video("v1", 30_000);
    video("v2", 45_000);
    playlist(P1, "Vídeos", item("v1", 0), item("v2", 0));
    schedule(P1, 10, null, null, null, null, List.of(), true);

    PlannedTemplate template = plan().templates().get(0);

    assertThat(template.kind()).isEqualTo(TemplateKind.VIDEOS);
    assertThat(template.contents()).hasSize(2);
  }

  @Test
  void aMixedPlaylistBecomesACycleOfDatedEventsForTheNextHours() {
    image("a");
    video("v", 20_000);
    web("w", "https://hermes.cefa.es/");
    playlist(P1, "Mixta", item("a", 10), item("v", 0), item("w", 30));
    schedule(P1, 10, null, null, "08:00", "09:00", List.of(1), false);

    AracastPlan plan = plan();

    assertThat(plan.rolling()).isTrue();
    assertThat(plan.templates())
        .extracting(PlannedTemplate::name, PlannedTemplate::kind)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Mixta 1 ~ctv-11111111-1", TemplateKind.IMAGE),
            org.assertj.core.groups.Tuple.tuple("Mixta 2 ~ctv-11111111-2", TemplateKind.VIDEOS),
            org.assertj.core.groups.Tuple.tuple("Mixta 3 ~ctv-11111111-3", TemplateKind.WEB));
    assertThat(plan.events()).allMatch(e -> e.date().equals(MONDAY) && e.days().isEmpty());
    assertThat(plan.events())
        .extracting(PlannedEvent::start, PlannedEvent::end)
        .startsWith(
            org.assertj.core.groups.Tuple.tuple(LocalTime.of(8, 0, 0), LocalTime.of(8, 0, 10)),
            org.assertj.core.groups.Tuple.tuple(LocalTime.of(8, 0, 10), LocalTime.of(8, 0, 30)),
            org.assertj.core.groups.Tuple.tuple(LocalTime.of(8, 0, 30), LocalTime.of(8, 1, 0)),
            org.assertj.core.groups.Tuple.tuple(LocalTime.of(8, 1, 0), LocalTime.of(8, 1, 10)));
    assertThat(plan.events().get(plan.events().size() - 1).end()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void aCycleOnlyCoversTheWindowNotTheWholeDay() {
    image("a");
    web("w", "https://hermes.cefa.es/");
    playlist(P1, "Todo el dia", item("a", 10), item("w", 20));
    schedule(P1, 10, null, null, null, null, List.of(), true);

    AracastPlan plan = plan();

    assertThat(plan.events()).hasSize(2 * (AracastPlanner.ROLLING_MINUTES * 60 / 30));
    PlannedEvent last = plan.events().get(plan.events().size() - 1);
    assertThat(last.end()).isEqualTo(LocalTime.of(11, 0));
  }

  @Test
  void aFastCycleShrinksTheWindowInsteadOfFailing() {
    web("w1", "https://a.example/");
    web("w2", "https://b.example/");
    web("w3", "https://c.example/");
    playlist(P1, "Rapida", item("w1", 1), item("w2", 1), item("w3", 1));
    schedule(P1, 10, null, null, null, null, List.of(), true);

    AracastPlan plan = plan();

    assertThat(plan.events().size()).isLessThanOrEqualTo(AracastPlanner.MAX_EVENTS);
    assertThat(plan.events().get(plan.events().size() - 1).end()).isAfter(LocalTime.of(8, 20));
  }

  @Test
  void aCycleStartingLaterOnlyEmitsTheEventsInsideTheWindow() {
    web("w1", "https://a.example/");
    web("w2", "https://b.example/");
    playlist(P1, "Tarde", item("w1", 30), item("w2", 30));
    schedule(P1, 10, null, null, "15:00", "16:00", List.of(1), false);

    assertThat(plan().events()).isEmpty();
  }

  @Test
  void imagesWithDifferentDurationsShareOneSlideshowWithRepeatedSlides() {
    image("a");
    image("b");
    playlist(P1, "Tiempos", item("a", 5), item("b", 15));
    schedule(P1, 10, null, null, null, null, List.of(), true);

    AracastPlan plan = plan();

    assertThat(plan.rolling()).isFalse();
    assertThat(plan.events()).hasSize(1);
    PlannedTemplate template = plan.templates().get(0);
    assertThat(template.kind()).isEqualTo(TemplateKind.SLIDESHOW);
    assertThat(template.secondsPerSlide()).isEqualTo(5);
    assertThat(template.contents())
        .extracting(PlannedContent::sha256)
        .containsExactly("sha-a", "sha-b", "sha-b", "sha-b");
  }

  @Test
  void theIdleScreenFollowsTheChosenLogo() {
    AracastPlan cefa = plan();
    AracastPlan algontec =
        AracastPlanner.plan(
            new ManifestDocument(
                1,
                null,
                UUID.randomUUID(),
                "Europe/Madrid",
                null,
                files,
                assets,
                playlists,
                schedules),
            NOW,
            IdleLogo.ALGONTEC);

    assertThat(cefa.idle().name()).isEqualTo("Pantalla en reposo CEFA ~ctv-0000cefa");
    assertThat(algontec.idle().name()).isEqualTo("Pantalla en reposo ALGONTEC ~ctv-0a190000");
    assertThat(cefa.idle().contents().get(0).sha256()).startsWith(AracastPlanner.BUILTIN);
    assertThat(cefa.allTemplates()).contains(cefa.idle());
    assertThat(AracastTemplateNames.isManaged(algontec.idle().name())).isTrue();
  }

  @Test
  void rejectsAnEmptyPlaylistWithAClearMessage() {
    playlist(P1, "Vacía");
    schedule(P1, 10, null, null, null, null, List.of(), true);

    assertThatThrownBy(this::plan)
        .isInstanceOf(UnsupportedPlaylistException.class)
        .hasMessageContaining("Vacía")
        .hasMessageContaining("no tiene elementos");
  }

  @Test
  void anEmptyRestPlaylistDoesNotBlockTheSchedule() {
    web("w", "https://hermes.cefa.es/");
    playlist(P1, "Contenido");
    playlist(P2, "Web", item("w", 60));
    fallback = P1;
    schedule(P2, 10, null, null, null, null, List.of(), true);

    AracastPlan plan = plan();

    assertThat(plan.events()).hasSize(1);
    assertThat(plan.events().get(0).template()).startsWith("Web");
  }

  @Test
  void ignoresAnEmptyPlaylistNoScheduleUses() {
    web("w", "https://hermes.cefa.es/");
    playlist(P1, "Sin usar");
    playlist(P2, "Web", item("w", 60));
    schedule(P2, 10, null, null, null, null, List.of(), true);

    assertThat(plan().events()).hasSize(1);
  }

  @Test
  void ignoresDisabledSchedules() {
    image("a");
    playlist(P1, "Apagada", item("a", 10));
    schedules.add(
        new ScheduleEntry(
            UUID.randomUUID().toString(), P1, 10, null, null, null, null, List.of(), true, false));

    assertThat(plan().events()).isEmpty();
  }

  @Test
  void templateNamesNeverContainCharactersThePlayerTreatsAsPatterns() {
    image("a");
    playlist(P1, "Oficinas [planta 1] (nueva) +*?", item("a", 10));
    schedule(P1, 10, null, null, null, null, List.of(), true);

    String name = plan().templates().get(0).name();

    assertThat(name).isEqualTo("Oficinas planta 1 nueva ~ctv-11111111");
    assertThat(name).doesNotContainPattern("[\\[\\]()+*?{}|^$\\\\]");
  }

  @Test
  void theTemplateOnAirIsTheOneOfTheEventThatContainsTheInstant() {
    image("a");
    image("b");
    playlist(P1, "Base", item("a", 10));
    playlist(P2, "Aviso", item("b", 10));
    schedule(P1, 10, null, null, null, null, List.of(), true);
    schedule(P2, 50, null, null, "10:00", "12:00", List.of(), false);
    AracastPlan plan = plan();

    assertThat(AracastPlanner.activeTemplate(plan, MONDAY.atTime(9, 0)))
        .isEqualTo("Base ~ctv-11111111");
    assertThat(AracastPlanner.activeTemplate(plan, MONDAY.atTime(11, 0)))
        .isEqualTo("Aviso ~ctv-22222222");
    assertThat(AracastPlanner.activeTemplate(plan(), MONDAY.atTime(23, 59, 45)))
        .isEqualTo(plan.idle().name());
  }
}
