package es.cefa.tv.core.aracast.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.client.AracastFtpClient;
import es.cefa.tv.core.aracast.dto.PlayerStatus;
import es.cefa.tv.core.aracast.dto.PlayerTemplate;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.ics.AracastIcsCodec;
import es.cefa.tv.core.aracast.management.AracastLocks;
import es.cefa.tv.core.aracast.plan.AracastPlan;
import es.cefa.tv.core.aracast.plan.AracastPlanner;
import es.cefa.tv.core.aracast.plan.PlannedContent;
import es.cefa.tv.core.aracast.plan.PlannedEvent;
import es.cefa.tv.core.aracast.plan.PlannedTemplate;
import es.cefa.tv.core.aracast.plan.TemplateKind;
import es.cefa.tv.core.aracast.service.AracastQueryService;
import es.cefa.tv.core.aracast.template.TemplateBuilder;
import es.cefa.tv.core.player.model.IdleLogo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class AracastPublisherTest {

  private static final String HOST = "10.0.0.5";
  private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  private static final PlannedTemplate IMAGE =
      new PlannedTemplate(
          "Oficinas ~ctv-11111111",
          TemplateKind.IMAGE,
          List.of(new PlannedContent("sha-a", "logo.png", "image/png")),
          10,
          null,
          true);
  private static final PlannedTemplate IDLE = AracastPlanner.idleTemplate(IdleLogo.CEFA);
  private static final AracastPlan PLAN =
      new AracastPlan(
          List.of(IMAGE),
          List.of(
              new PlannedEvent(
                  IMAGE.name(),
                  LocalTime.MIDNIGHT,
                  LocalTime.of(23, 59, 59),
                  Set.of(DayOfWeek.values()),
                  null)),
          IDLE,
          false);

  @TempDir Path dir;

  private AracastCommandClient commands;
  private AracastFtpClient ftp;
  private AracastQueryService query;
  private AracastContentService contents;
  private AracastPublisher publisher;

  @BeforeEach
  void setUp() throws Exception {
    commands = mock(AracastCommandClient.class);
    ftp = mock(AracastFtpClient.class);
    query = mock(AracastQueryService.class);
    contents = mock(AracastContentService.class);
    ObjectMapper mapper = new ObjectMapper();
    when(contents.playerName(any(), any()))
        .thenAnswer(
            call -> {
              PlannedContent content = call.getArgument(0);
              return content.sha256().startsWith(AracastPlanner.BUILTIN)
                  ? "reposo_def.png"
                  : "logo_abc.png";
            });
    when(contents.fetch(any(), any(), any()))
        .thenAnswer(call -> Files.writeString(dir.resolve((String) call.getArgument(1)), "png"));
    when(query.getStatus(HOST)).thenReturn(status("", "basic_json"));
    when(commands.send(eq(HOST), eq("Template.getContent"), anyMap()))
        .thenReturn(mapper.readTree("{\"result\":{\"files\":{}}}"));
    publisher =
        new AracastPublisher(
            commands,
            ftp,
            query,
            contents,
            new TemplateBuilder(mapper),
            new AracastIcsCodec(),
            new AracastLocks());
  }

  private static PlayerStatus status(String currentTemplate, String defaultTemplate) {
    return new PlayerStatus(
        HOST,
        "2.6.19",
        "Dual",
        "00:01",
        HOST,
        "1 day",
        "1920x1080",
        currentTemplate,
        null,
        null,
        defaultTemplate,
        1L,
        1L,
        "2026-10-19 10:00:00",
        "Europe/Madrid",
        0L);
  }

  private Map<String, byte[]> uploadedZips() {
    Map<String, byte[]> zips = new LinkedHashMap<>();
    for (var invocation : mockingDetails(ftp).getInvocations()) {
      if (invocation.getMethod().getName().equals("upload")) {
        String name = invocation.getArgument(1);
        if (name.endsWith(".zip")) {
          try {
            zips.put(name, ((InputStream) invocation.getArgument(2)).readAllBytes());
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }
      }
    }
    return zips;
  }

  private static String sha1(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
  }

  private List<PlayerTemplate> installedWithTheCurrentHashes() throws Exception {
    List<PlayerTemplate> installed = new ArrayList<>();
    for (var zip : uploadedZips().entrySet()) {
      String name = zip.getKey().substring(0, zip.getKey().length() - 4);
      installed.add(
          new PlayerTemplate(
              name, zip.getKey(), sha1(zip.getValue()), 1, Instant.now(), List.of(), null));
    }
    return installed;
  }

  @Test
  void publishesContentsTemplatesAndEventsInTheDesktopAppOrder() {
    when(query.getTemplates(HOST)).thenReturn(List.of());

    AracastPublisher.Result result = publisher.publish(HOST, PLAN, ZONE);

    InOrder order = inOrder(ftp, commands);
    order.verify(ftp).upload(eq(HOST), eq("lock"), any(InputStream.class));
    order.verify(ftp).uploadAll(eq(HOST), anyMap());
    order.verify(commands).send(HOST, "Template.uploadContent", Map.of("file", "logo_abc.png"));
    order.verify(ftp).upload(eq(HOST), eq("Oficinas ~ctv-11111111.zip"), any(InputStream.class));
    order
        .verify(commands)
        .send(
            HOST,
            "Template.uploadTemplate",
            Map.of("file", "Oficinas ~ctv-11111111.zip", "default", false));
    order.verify(ftp).upload(eq(HOST), eq("events.ics"), any(InputStream.class));
    order.verify(commands).send(HOST, "Player.loadEvents");
    order.verify(commands).send(HOST, "Player.updateEvents");
    order.verify(ftp).delete(HOST, "lock");
    assertThat(result.templatesInstalled()).isEqualTo(2);
    assertThat(result.contentsUploaded()).isEqualTo(2);
    assertThat(result.events()).isEqualTo(1);
  }

  @Test
  void installsTheIdleScreenAsTheDefaultTemplate() {
    when(query.getTemplates(HOST)).thenReturn(List.of());

    publisher.publish(HOST, PLAN, ZONE);

    verify(commands)
        .send(
            HOST, "Template.uploadTemplate", Map.of("file", IDLE.name() + ".zip", "default", true));
  }

  @Test
  void doesNotReinstallTemplatesWhoseZipHasNotChangedAndKeepsTheDefault() throws Exception {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    publisher.publish(HOST, PLAN, ZONE);
    List<PlayerTemplate> installed = installedWithTheCurrentHashes();
    org.mockito.Mockito.clearInvocations(ftp, commands);
    when(query.getTemplates(HOST)).thenReturn(installed);
    when(query.getStatus(HOST)).thenReturn(status(IMAGE.name(), IDLE.name()));

    AracastPublisher.Result second = publisher.publish(HOST, PLAN, ZONE);

    assertThat(second.templatesInstalled()).isZero();
    verify(commands, never()).send(eq(HOST), eq("Template.uploadTemplate"), anyMap());
    verify(commands).send(HOST, "Player.updateEvents");
  }

  @Test
  void reinstallsTheIdleTemplateWhenItIsNotTheDefault() throws Exception {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    publisher.publish(HOST, PLAN, ZONE);
    List<PlayerTemplate> installed = installedWithTheCurrentHashes();
    org.mockito.Mockito.clearInvocations(ftp, commands);
    when(query.getTemplates(HOST)).thenReturn(installed);
    when(query.getStatus(HOST)).thenReturn(status(IMAGE.name(), "basic_json"));

    AracastPublisher.Result second = publisher.publish(HOST, PLAN, ZONE);

    assertThat(second.templatesInstalled()).isEqualTo(1);
    verify(commands)
        .send(
            HOST, "Template.uploadTemplate", Map.of("file", IDLE.name() + ".zip", "default", true));
  }

  @Test
  void removesOnlyTheStaleTemplatesThatCefaTvCreated() {
    when(query.getTemplates(HOST))
        .thenReturn(
            List.of(
                new PlayerTemplate(
                    "Vieja [ctv-99999999]",
                    "Vieja [ctv-99999999].zip",
                    "h",
                    1,
                    null,
                    List.of(),
                    null),
                new PlayerTemplate(
                    "Otra ~ctv-88888888", "Otra ~ctv-88888888.zip", "h", 1, null, List.of(), null),
                new PlayerTemplate(
                    "CEFA Oficinas", "CEFA Oficinas.zip", "h", 1, null, List.of(), null)));

    AracastPublisher.Result result = publisher.publish(HOST, PLAN, ZONE);

    verify(commands)
        .send(HOST, "Template.deleteTemplate", Map.of("file", "Vieja [ctv-99999999].zip"));
    verify(commands)
        .send(HOST, "Template.deleteTemplate", Map.of("file", "Otra ~ctv-88888888.zip"));
    verify(commands, never())
        .send(HOST, "Template.deleteTemplate", Map.of("file", "CEFA Oficinas.zip"));
    assertThat(result.templatesRemoved()).isEqualTo(2);
  }

  @Test
  void theOtherIdleTemplateIsRemovedWhenTheLogoChanges() {
    PlannedTemplate algontec = AracastPlanner.idleTemplate(IdleLogo.ALGONTEC);
    when(query.getTemplates(HOST))
        .thenReturn(
            List.of(
                new PlayerTemplate(
                    algontec.name(), algontec.name() + ".zip", "h", 1, null, List.of(), null)));

    publisher.publish(HOST, PLAN, ZONE);

    verify(commands)
        .send(HOST, "Template.deleteTemplate", Map.of("file", algontec.name() + ".zip"));
  }

  @Test
  void releasesTheLockEvenWhenAnUploadFails() {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    org.mockito.Mockito.doThrow(new RuntimeException("FTP caído"))
        .when(ftp)
        .uploadAll(eq(HOST), anyMap());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> publisher.publish(HOST, PLAN, ZONE))
        .hasMessageContaining("FTP caído");

    verify(ftp).delete(HOST, "lock");
  }

  @Test
  void anEmptyPlanLeavesTheIdleScreenAllDayInsteadOfAnEmptyCalendar() {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    ArgumentCaptor<InputStream> upload = ArgumentCaptor.forClass(InputStream.class);

    publisher.publish(HOST, new AracastPlan(List.of(), List.of(), IDLE, false), ZONE);

    verify(ftp).upload(eq(HOST), eq("events.ics"), upload.capture());
    try {
      assertThat(new String(upload.getValue().readAllBytes()))
          .contains("X-ARACAST-CONTENT:" + IDLE.name())
          .contains("BEGIN:VEVENT");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void datedEventsAreWrittenWithTheirDateAndNoRepetition() throws Exception {
    PlannedEvent dated =
        new PlannedEvent(
            IMAGE.name(),
            LocalTime.of(8, 0),
            LocalTime.of(8, 0, 10),
            Set.of(),
            java.time.LocalDate.of(2026, 10, 19));
    AracastPlan plan = new AracastPlan(List.of(IMAGE), List.of(dated), IDLE, true);
    when(query.getTemplates(HOST)).thenReturn(List.of());
    ArgumentCaptor<InputStream> upload = ArgumentCaptor.forClass(InputStream.class);

    publisher.publish(HOST, plan, ZONE);

    verify(ftp).upload(eq(HOST), eq("events.ics"), upload.capture());
    assertThat(new String(upload.getValue().readAllBytes()))
        .contains("DTSTART:20261019T080000")
        .contains("DTEND:20261019T080010")
        .contains("RRULE:FREQ=DAILY;COUNT=1");
  }

  @Test
  void switchesToTheTemplateThatShouldBeOnAirRightAfterPublishing() {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    when(query.getStatus(HOST)).thenReturn(status("basic_json", "basic_json"));

    publisher.publish(HOST, PLAN, ZONE);

    InOrder order = inOrder(commands);
    order.verify(commands).send(HOST, "Player.updateEvents");
    order.verify(commands).send(HOST, "Template.changeTemplate", Map.of("template", IMAGE.name()));
  }

  @Test
  void switchesAlsoWhenTheTemplateOnAirWasJustReplaced() {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    when(query.getStatus(HOST)).thenReturn(status(IMAGE.name(), "basic_json"));

    publisher.publish(HOST, PLAN, ZONE);

    verify(commands).send(HOST, "Template.changeTemplate", Map.of("template", IMAGE.name()));
  }

  @Test
  void aFailedImmediateSwitchDoesNotFailThePublication() {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    when(commands.send(eq(HOST), eq("Template.changeTemplate"), anyMap()))
        .thenThrow(new AracastException("rechazado"));

    AracastPublisher.Result result = publisher.publish(HOST, PLAN, ZONE);

    assertThat(result.events()).isEqualTo(1);
  }
}
