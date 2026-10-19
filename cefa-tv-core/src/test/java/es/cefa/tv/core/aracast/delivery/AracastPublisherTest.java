package es.cefa.tv.core.aracast.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.client.AracastFtpClient;
import es.cefa.tv.core.aracast.dto.PlayerTemplate;
import es.cefa.tv.core.aracast.ics.AracastIcsCodec;
import es.cefa.tv.core.aracast.plan.AracastPlan;
import es.cefa.tv.core.aracast.plan.PlannedContent;
import es.cefa.tv.core.aracast.plan.PlannedEvent;
import es.cefa.tv.core.aracast.plan.PlannedTemplate;
import es.cefa.tv.core.aracast.plan.TemplateKind;
import es.cefa.tv.core.aracast.service.AracastQueryService;
import es.cefa.tv.core.aracast.template.TemplateBuilder;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HexFormat;
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
  private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Europe/Madrid");
  private static final PlannedTemplate IMAGE =
      new PlannedTemplate(
          "Oficinas ~ctv-11111111",
          TemplateKind.IMAGE,
          List.of(new PlannedContent("sha-a", "logo.png", "image/png")),
          10,
          null,
          true);
  private static final AracastPlan PLAN =
      new AracastPlan(
          List.of(IMAGE),
          List.of(
              new PlannedEvent(
                  IMAGE.name(),
                  LocalTime.MIDNIGHT,
                  LocalTime.of(23, 59, 59),
                  Set.of(DayOfWeek.values()))));

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
    when(contents.playerName(any(), any())).thenReturn("logo_abc.png");
    Path local = Files.writeString(dir.resolve("logo_abc.png"), "png");
    when(contents.fetch(any(), eq("logo_abc.png"), any())).thenReturn(local);
    when(query.getStatus(HOST)).thenReturn(status(""));
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
            new es.cefa.tv.core.aracast.management.AracastLocks());
  }

  @Test
  void publishesContentsTemplateAndEventsInTheDesktopAppOrder() {
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
    assertThat(result.templatesInstalled()).isEqualTo(1);
    assertThat(result.contentsUploaded()).isEqualTo(1);
    assertThat(result.events()).isEqualTo(1);
  }

  @Test
  void doesNotReinstallATemplateWhoseZipHasNotChanged() throws Exception {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    publisher.publish(HOST, PLAN, ZONE);
    ArgumentCaptor<InputStream> zip = ArgumentCaptor.forClass(InputStream.class);
    verify(ftp).upload(eq(HOST), eq("Oficinas ~ctv-11111111.zip"), zip.capture());
    String hash =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-1").digest(zip.getValue().readAllBytes()));
    ftpReset();
    when(query.getTemplates(HOST))
        .thenReturn(
            List.of(
                new PlayerTemplate(
                    IMAGE.name(), IMAGE.name() + ".zip", hash, 1, Instant.now(), List.of(), null)));

    AracastPublisher.Result second = publisher.publish(HOST, PLAN, ZONE);

    assertThat(second.templatesInstalled()).isZero();
    verify(commands, never()).send(eq(HOST), eq("Template.uploadTemplate"), anyMap());
    verify(commands).send(HOST, "Player.updateEvents");
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
                    "CEFA Oficinas", "CEFA Oficinas.zip", "h", 1, null, List.of(), null)));

    AracastPublisher.Result result = publisher.publish(HOST, PLAN, ZONE);

    verify(commands)
        .send(HOST, "Template.deleteTemplate", Map.of("file", "Vieja [ctv-99999999].zip"));
    verify(commands, never())
        .send(HOST, "Template.deleteTemplate", Map.of("file", "CEFA Oficinas.zip"));
    assertThat(result.templatesRemoved()).isEqualTo(1);
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

  private void ftpReset() {
    org.mockito.Mockito.clearInvocations(ftp, commands);
  }

  @Test
  void anEmptyPlanLeavesTheDefaultTemplateInsteadOfAnEmptyCalendar() {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    ArgumentCaptor<InputStream> upload = ArgumentCaptor.forClass(InputStream.class);

    publisher.publish(HOST, new AracastPlan(List.of(), List.of()), ZONE);

    verify(ftp).upload(eq(HOST), eq("events.ics"), upload.capture());
    try {
      assertThat(new String(upload.getValue().readAllBytes()))
          .contains("X-ARACAST-CONTENT:basic_json")
          .contains("BEGIN:VEVENT");
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static es.cefa.tv.core.aracast.dto.PlayerStatus status(String currentTemplate) {
    return new es.cefa.tv.core.aracast.dto.PlayerStatus(
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
        "basic_json",
        1L,
        1L,
        "2026-10-19 10:00:00",
        "Europe/Madrid",
        0L);
  }

  @Test
  void switchesToTheTemplateThatShouldBeOnAirRightAfterPublishing() {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    when(query.getStatus(HOST)).thenReturn(status("basic_json"));

    publisher.publish(HOST, PLAN, ZONE);

    InOrder order = inOrder(commands);
    order.verify(commands).send(HOST, "Player.updateEvents");
    order.verify(commands).send(HOST, "Template.changeTemplate", Map.of("template", IMAGE.name()));
  }

  @Test
  void switchesAlsoWhenTheTemplateOnAirWasJustReplaced() {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    when(query.getStatus(HOST)).thenReturn(status(IMAGE.name()));

    publisher.publish(HOST, PLAN, ZONE);

    verify(commands).send(HOST, "Template.changeTemplate", Map.of("template", IMAGE.name()));
  }

  @Test
  void doesNotSwitchWhenTheRightTemplateIsOnAirAndUnchanged() throws Exception {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    publisher.publish(HOST, PLAN, ZONE);
    ArgumentCaptor<InputStream> zip = ArgumentCaptor.forClass(InputStream.class);
    verify(ftp).upload(eq(HOST), eq("Oficinas ~ctv-11111111.zip"), zip.capture());
    String hash =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-1").digest(zip.getValue().readAllBytes()));
    org.mockito.Mockito.clearInvocations(ftp, commands);
    when(query.getTemplates(HOST))
        .thenReturn(
            List.of(
                new PlayerTemplate(
                    IMAGE.name(), IMAGE.name() + ".zip", hash, 1, Instant.now(), List.of(), null)));
    when(query.getStatus(HOST)).thenReturn(status(IMAGE.name()));

    publisher.publish(HOST, PLAN, ZONE);

    verify(commands, never()).send(eq(HOST), eq("Template.changeTemplate"), anyMap());
  }

  @Test
  void aFailedImmediateSwitchDoesNotFailThePublication() {
    when(query.getTemplates(HOST)).thenReturn(List.of());
    when(commands.send(eq(HOST), eq("Template.changeTemplate"), anyMap()))
        .thenThrow(new es.cefa.tv.core.aracast.exception.AracastException("rechazado"));

    AracastPublisher.Result result = publisher.publish(HOST, PLAN, ZONE);

    assertThat(result.events()).isEqualTo(1);
  }

  @Test
  void removesTheOldTemplatesWithBracketsThatThePlayerCannotUse() {
    when(query.getTemplates(HOST))
        .thenReturn(
            List.of(
                new PlayerTemplate(
                    "Vieja ~ctv-88888888",
                    "Vieja ~ctv-88888888.zip",
                    "h",
                    1,
                    null,
                    List.of(),
                    null)));

    publisher.publish(HOST, PLAN, ZONE);

    verify(commands)
        .send(HOST, "Template.deleteTemplate", Map.of("file", "Vieja ~ctv-88888888.zip"));
  }
}
