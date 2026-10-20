package es.cefa.tv.core.aracast.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.core.aracast.client.AracastCommandClient;
import es.cefa.tv.core.aracast.client.AracastFtpClient;
import es.cefa.tv.core.aracast.dto.PlayerTemplate;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.ics.AracastIcsCodec;
import es.cefa.tv.core.aracast.ics.IcsEntry;
import es.cefa.tv.core.aracast.ics.IcsSchedule;
import es.cefa.tv.core.aracast.management.AracastLocks;
import es.cefa.tv.core.aracast.plan.AracastPlan;
import es.cefa.tv.core.aracast.plan.AracastPlanner;
import es.cefa.tv.core.aracast.plan.AracastTemplateNames;
import es.cefa.tv.core.aracast.plan.PlannedContent;
import es.cefa.tv.core.aracast.plan.PlannedEvent;
import es.cefa.tv.core.aracast.plan.PlannedTemplate;
import es.cefa.tv.core.aracast.plan.TemplateKind;
import es.cefa.tv.core.aracast.service.AracastQueryService;
import es.cefa.tv.core.aracast.template.TemplateBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deja en un Aracast lo que dice un {@link AracastPlan}: sube los contenidos que faltan, instala
 * las plantillas nuevas o cambiadas, publica el {@code events.ics} y borra las plantillas de CEFA
 * TV que ya no se usan. Sigue el flujo de la aplicación de escritorio, con el fichero {@code lock}
 * que bloquea el sincronizador del player mientras dura.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AracastPublisher {

  private static final String LOCK_FILE = "lock";
  private static final String EVENTS_FILE = "events.ics";
  private static final String SCHEDULE_NAME = "CEFA TV";
  private static final LocalDateTime FIXED_ZIP_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

  private final AracastCommandClient commands;
  private final AracastFtpClient ftp;
  private final AracastQueryService query;
  private final AracastContentService contents;
  private final TemplateBuilder templates;
  private final AracastIcsCodec ics;
  private final AracastLocks locks;

  /**
   * Publica un plan en un player.
   *
   * @param host IP o nombre del player
   * @param plan plantillas y eventos
   * @param zone zona horaria del player, para saber qué plantilla toca ahora
   * @return resumen de lo que se hizo
   */
  public Result publish(String host, AracastPlan plan, ZoneId zone) {
    return locks.withLock(host, () -> publishLocked(host, plan, zone));
  }

  private Result publishLocked(String host, AracastPlan plan, ZoneId zone) {
    Path work = createWorkDir();
    try {
      Map<String, PlayerTemplate> installed = new LinkedHashMap<>();
      query.getTemplates(host).forEach(t -> installed.put(t.name(), t));
      String currentDefault = defaultTemplateOf(host);
      Map<String, String> fileNames = new LinkedHashMap<>();
      for (PlannedTemplate template : plan.allTemplates()) {
        for (PlannedContent content : template.contents()) {
          if (template.kind() != TemplateKind.HTML) {
            fileNames.computeIfAbsent(content.sha256(), sha -> contents.playerName(content, work));
          }
        }
      }
      Map<String, Path> missing = missingContents(host, plan, fileNames, work);
      Map<PlannedTemplate, Prepared> toInstall = new LinkedHashMap<>();
      for (PlannedTemplate template : plan.allTemplates()) {
        Prepared prepared = prepare(template, fileNames, work);
        PlayerTemplate current = installed.get(template.name());
        boolean notDefault = template == plan.idle() && !template.name().equals(currentDefault);
        if (current == null || !prepared.sha1().equalsIgnoreCase(current.hash()) || notDefault) {
          toInstall.put(template, prepared);
        }
      }
      String events = ics.encode(toSchedule(plan));
      withLock(
          host,
          () -> {
            if (!missing.isEmpty()) {
              ftp.uploadAll(host, missing);
              for (String name : missing.keySet()) {
                commands.send(host, "Template.uploadContent", Map.of("file", name));
              }
            }
            for (var entry : toInstall.entrySet()) {
              install(host, entry.getKey(), entry.getValue(), entry.getKey() == plan.idle());
            }
            ftp.upload(host, EVENTS_FILE, stream(events.getBytes(StandardCharsets.UTF_8)));
            commands.send(host, "Player.loadEvents");
            commands.send(host, "Player.updateEvents");
          });
      switchNow(host, plan, zone, toInstall.keySet());
      int removed = removeStale(host, plan, installed);
      log.info(
          "Publicado en {}: {} plantillas nuevas, {} contenidos subidos, {} eventos, {} borradas",
          host,
          toInstall.size(),
          missing.size(),
          plan.events().size(),
          removed);
      return new Result(toInstall.size(), missing.size(), plan.events().size(), removed);
    } finally {
      deleteRecursively(work);
    }
  }

  private Map<String, Path> missingContents(
      String host, AracastPlan plan, Map<String, String> fileNames, Path work) {
    Map<String, Path> missing = new LinkedHashMap<>();
    if (fileNames.isEmpty()) {
      return missing;
    }
    JsonNode existing =
        commands
            .send(
                host,
                "Template.getContent",
                Map.of("files", List.copyOf(new java.util.LinkedHashSet<>(fileNames.values()))))
            .path("result")
            .path("files");
    Set<String> present = new HashSet<>();
    existing.fieldNames().forEachRemaining(present::add);
    Map<String, PlannedContent> bySha = new LinkedHashMap<>();
    plan.allTemplates().forEach(t -> t.contents().forEach(c -> bySha.putIfAbsent(c.sha256(), c)));
    fileNames.forEach(
        (sha, name) -> {
          if (!present.contains(name)) {
            missing.put(name, contents.fetch(bySha.get(sha), name, work));
          }
        });
    return missing;
  }

  private Prepared prepare(PlannedTemplate template, Map<String, String> fileNames, Path work) {
    List<String> names = template.contents().stream().map(c -> fileNames.get(c.sha256())).toList();
    String webName = null;
    String json =
        switch (template.kind()) {
          case IMAGE -> templates.image(names.get(0));
          case SLIDESHOW -> templates.slideshow(names, template.secondsPerSlide());
          case VIDEOS -> templates.videos(names, template.muted());
          case WEB -> templates.web(template.url(), true, "#ffffff");
          case HTML ->
              templates.web(
                  "http://localhost/" + webName(template) + "/index.html", true, "#ffffff");
        };
    if (template.kind() == TemplateKind.HTML) {
      webName = webName(template);
    }
    byte[] zip = zipOf("main.json", json.getBytes(StandardCharsets.UTF_8));
    return new Prepared(zip, sha1(zip), webName);
  }

  private void install(
      String host, PlannedTemplate template, Prepared prepared, boolean makeDefault) {
    if (prepared.webName() != null) {
      byte[] html = contents.read(template.contents().get(0).sha256());
      byte[] webZip = zipOf("index.html", html);
      String fileName = prepared.webName() + ".zip";
      ftp.upload(host, fileName, stream(webZip));
      commands.send(host, "Template.uploadWeb", Map.of("file", fileName));
    }
    String zipName = template.name() + ".zip";
    ftp.upload(host, zipName, stream(prepared.zip()));
    commands.send(host, "Template.uploadTemplate", Map.of("file", zipName, "default", makeDefault));
  }

  /**
   * Pone en emisión ya la plantilla que toca, sin esperar al siguiente cambio del calendario: si el
   * player sigue mostrando otra, o una que se acaba de sustituir, no se vería lo nuevo hasta
   * entonces. Un fallo aquí no invalida la publicación.
   */
  private void switchNow(
      String host, AracastPlan plan, ZoneId zone, Set<PlannedTemplate> reinstalled) {
    try {
      String target = AracastPlanner.activeTemplate(plan, LocalDateTime.now(zone));
      String current = query.getStatus(host).currentTemplate();
      boolean replaced = reinstalled.stream().anyMatch(t -> t.name().equals(target));
      if (!target.equals(current) || replaced) {
        commands.send(host, "Template.changeTemplate", Map.of("template", target));
      }
    } catch (AracastException e) {
      log.warn("No se pudo cambiar ya la plantilla de {}: {}", host, e.getMessage());
    }
  }

  private String defaultTemplateOf(String host) {
    try {
      return query.getStatus(host).defaultTemplate();
    } catch (AracastException e) {
      return null;
    }
  }

  private int removeStale(String host, AracastPlan plan, Map<String, PlayerTemplate> installed) {
    Set<String> wanted = new HashSet<>();
    plan.allTemplates().forEach(t -> wanted.add(t.name()));
    int removed = 0;
    for (PlayerTemplate template : installed.values()) {
      if (AracastTemplateNames.isManaged(template.name()) && !wanted.contains(template.name())) {
        try {
          commands.send(host, "Template.deleteTemplate", Map.of("file", template.fileName()));
          removed++;
        } catch (AracastException e) {
          log.warn(
              "No se pudo borrar la plantilla {} de {}: {}", template.name(), host, e.getMessage());
        }
      }
    }
    return removed;
  }

  private static IcsSchedule toSchedule(AracastPlan plan) {
    if (plan.events().isEmpty()) {
      return new IcsSchedule(
          SCHEDULE_NAME,
          List.of(
              new IcsEntry(
                  plan.idle().name(), LocalTime.MIDNIGHT, LocalTime.of(23, 59, 30), List.of())),
          0);
    }
    List<IcsEntry> entries =
        plan.events().stream()
            .map(
                (PlannedEvent e) ->
                    new IcsEntry(
                        e.template(),
                        e.start(),
                        e.end(),
                        e.days().stream().sorted().toList(),
                        e.date()))
            .toList();
    return new IcsSchedule(SCHEDULE_NAME, entries, 0);
  }

  private void withLock(String host, Runnable action) {
    ftp.upload(
        host, LOCK_FILE, stream(("cefa-tv " + Instant.now()).getBytes(StandardCharsets.UTF_8)));
    try {
      action.run();
    } finally {
      ftp.delete(host, LOCK_FILE);
    }
  }

  private static String webName(PlannedTemplate template) {
    return "ctv-" + template.contents().get(0).sha256().substring(0, 12);
  }

  private static byte[] zipOf(String entryName, byte[] content) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out)) {
      ZipEntry entry = new ZipEntry(entryName);
      entry.setTimeLocal(FIXED_ZIP_TIME);
      zip.putNextEntry(entry);
      zip.write(content);
      zip.closeEntry();
      zip.finish();
      return out.toByteArray();
    } catch (IOException e) {
      throw new AracastException("No se pudo generar el zip de " + entryName, e);
    }
  }

  private static String sha1(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ByteArrayInputStream stream(byte[] bytes) {
    return new ByteArrayInputStream(bytes);
  }

  private static Path createWorkDir() {
    try {
      return Files.createTempDirectory("cefa-tv-aracast-");
    } catch (IOException e) {
      throw new AracastException("No se pudo crear la carpeta de trabajo", e);
    }
  }

  private static void deleteRecursively(Path dir) {
    try (Stream<Path> paths = Files.walk(dir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException e) {
      log.warn("No se pudo borrar {}", dir);
    }
  }

  /**
   * Resumen de una publicación.
   *
   * @param templatesInstalled plantillas subidas
   * @param contentsUploaded contenidos subidos
   * @param events eventos de la programación
   * @param templatesRemoved plantillas de CEFA TV borradas por no usarse
   */
  public record Result(
      int templatesInstalled, int contentsUploaded, int events, int templatesRemoved) {}

  private record Prepared(byte[] zip, String sha1, String webName) {}
}
