package es.cefa.tv.core.aracast.ics;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Convierte una {@link IcsSchedule} en el {@code events.ics} que entiende el player y al revés.
 *
 * <p>El formato reproduce el de la aplicación de escritorio de Aracast: fecha simbólica de inicio,
 * repetición con {@code RRULE} y propiedades {@code X-ARACAST-*}. Solo los eventos de tipo 3
 * (cambio de plantilla) se convierten en franjas; el resto se cuentan como no soportados.
 */
@Component
public class AracastIcsCodec {

  /** Lunes que se usa como fecha simbólica de inicio, igual que la aplicación de escritorio. */
  static final LocalDate ANCHOR_DATE = LocalDate.of(2000, 7, 3);

  /** Tipo de evento que cambia la plantilla en emisión. */
  static final String TYPE_TEMPLATE = "3";

  private static final String CRLF = "\r\n";
  private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
  private static final DateTimeFormatter UTC =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final Map<DayOfWeek, String> DAY_CODES = new EnumMap<>(DayOfWeek.class);

  static {
    String[] codes = {"MO", "TU", "WE", "TH", "FR", "SA", "SU"};
    for (DayOfWeek d : DayOfWeek.values()) {
      DAY_CODES.put(d, codes[d.ordinal()]);
    }
  }

  private final Clock clock;

  /** Crea el codec con el reloj del sistema. */
  public AracastIcsCodec() {
    this(Clock.systemUTC());
  }

  AracastIcsCodec(Clock clock) {
    this.clock = clock;
  }

  /**
   * Genera el contenido de {@code events.ics}.
   *
   * @param schedule programación ya validada
   * @return calendario iCal con finales de línea CRLF
   */
  public String encode(IcsSchedule schedule) {
    StringBuilder sb = new StringBuilder();
    line(sb, "BEGIN:VCALENDAR");
    line(sb, "PRODID:-//AraCast//iCal4j 1.0//ES");
    line(sb, "VERSION:2.0");
    line(sb, "CALSCALE:GREGORIAN");
    line(sb, "X-ARACAST-VERSION:4.0.0.85");
    line(sb, "X-ARACAST-SCHEDULE:" + clean(schedule.name()));
    line(sb, "X-ARACAST-UPDATE:true");
    String stamp = UTC.format(clock.instant());
    for (IcsEntry entry : schedule.entries()) {
      long durationMs = Duration.between(entry.start(), entry.end()).toMillis();
      line(sb, "BEGIN:VEVENT");
      line(sb, "DTSTAMP:" + stamp);
      LocalDate day = entry.date() == null ? ANCHOR_DATE : entry.date();
      line(sb, "DTSTART:" + LOCAL.format(LocalDateTime.of(day, entry.start())));
      line(sb, "DTEND:" + LOCAL.format(LocalDateTime.of(day, entry.end())));
      line(sb, "SUMMARY:" + escapeText(entry.template()));
      line(sb, "RRULE:" + rrule(entry));
      line(sb, "UID:" + UUID.randomUUID());
      line(sb, "X-ARACAST-CONTENT:" + clean(entry.template()));
      line(sb, "X-ARACAST-TYPE:" + TYPE_TEMPLATE);
      line(sb, "X-ARACAST-TRANSITION-IN:Corte");
      line(sb, "X-ARACAST-TRANSITION-OUT:Corte");
      line(sb, "X-ARACAST-DURATION:" + durationMs);
      line(sb, "END:VEVENT");
    }
    line(sb, "END:VCALENDAR");
    return sb.toString();
  }

  /**
   * Interpreta un {@code events.ics} del player.
   *
   * @param ics contenido del fichero
   * @return programación con las franjas de cambio de plantilla
   */
  public IcsSchedule decode(String ics) {
    List<String> lines = unfold(ics);
    String name = "";
    List<IcsEntry> entries = new ArrayList<>();
    int unsupported = 0;
    Map<String, String> event = null;
    for (String l : lines) {
      if (l.equals("BEGIN:VEVENT")) {
        event = new HashMap<>();
      } else if (l.equals("END:VEVENT")) {
        IcsEntry entry = event == null ? null : toEntry(event);
        if (entry == null) {
          unsupported++;
        } else {
          entries.add(entry);
        }
        event = null;
      } else {
        int colon = l.indexOf(':');
        if (colon <= 0) {
          continue;
        }
        String key = l.substring(0, colon);
        int semicolon = key.indexOf(';');
        if (semicolon > 0) {
          key = key.substring(0, semicolon);
        }
        String value = l.substring(colon + 1);
        if (event != null) {
          event.put(key.toUpperCase(), value);
        } else if (key.equalsIgnoreCase("X-ARACAST-SCHEDULE")) {
          name = value;
        }
      }
    }
    entries.sort((a, b) -> a.start().compareTo(b.start()));
    return new IcsSchedule(name, entries, unsupported);
  }

  private static IcsEntry toEntry(Map<String, String> event) {
    if (!TYPE_TEMPLATE.equals(event.getOrDefault("X-ARACAST-TYPE", "").trim())) {
      return null;
    }
    String template = event.getOrDefault("X-ARACAST-CONTENT", event.get("SUMMARY"));
    LocalTime start = parseTime(event.get("DTSTART"));
    if (template == null || start == null) {
      return null;
    }
    LocalTime end = parseTime(event.get("DTEND"));
    if (end == null) {
      String duration = event.get("X-ARACAST-DURATION");
      if (duration == null) {
        return null;
      }
      end = start.plus(Duration.ofMillis(Long.parseLong(duration.trim())));
    }
    return new IcsEntry(unescapeText(template), start, end, parseDays(event.get("RRULE")));
  }

  private static LocalTime parseTime(String value) {
    if (value == null) {
      return null;
    }
    String v = value.trim().replace("Z", "");
    int t = v.indexOf('T');
    if (t < 0 || v.length() < t + 7) {
      return null;
    }
    String hhmmss = v.substring(t + 1, t + 7);
    return LocalTime.of(
        Integer.parseInt(hhmmss.substring(0, 2)),
        Integer.parseInt(hhmmss.substring(2, 4)),
        Integer.parseInt(hhmmss.substring(4, 6)));
  }

  private static List<DayOfWeek> parseDays(String rrule) {
    if (rrule == null) {
      return List.of();
    }
    for (String part : rrule.split(";")) {
      if (part.toUpperCase().startsWith("BYDAY=")) {
        List<DayOfWeek> days = new ArrayList<>();
        for (String code : part.substring(6).split(",")) {
          DAY_CODES.forEach(
              (day, c) -> {
                if (code.trim().toUpperCase().endsWith(c)) {
                  days.add(day);
                }
              });
        }
        return days;
      }
    }
    return List.of();
  }

  private static String rrule(IcsEntry entry) {
    if (entry.date() != null) {
      return "FREQ=DAILY;COUNT=1";
    }
    if (entry.everyDay()) {
      return "FREQ=DAILY";
    }
    StringBuilder byDay = new StringBuilder();
    entry.days().stream()
        .distinct()
        .sorted()
        .forEach(d -> byDay.append(byDay.isEmpty() ? "" : ",").append(DAY_CODES.get(d)));
    return "FREQ=WEEKLY;BYDAY=" + byDay;
  }

  private static List<String> unfold(String ics) {
    List<String> result = new ArrayList<>();
    for (String raw : ics.split("\r?\n")) {
      if ((raw.startsWith(" ") || raw.startsWith("\t")) && !result.isEmpty()) {
        int last = result.size() - 1;
        result.set(last, result.get(last) + raw.substring(1));
      } else if (!raw.isEmpty()) {
        result.add(raw);
      }
    }
    return result;
  }

  private static String clean(String value) {
    return value.replace("\r", " ").replace("\n", " ").trim();
  }

  private static String escapeText(String value) {
    return clean(value).replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,");
  }

  private static String unescapeText(String value) {
    return value.replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\");
  }

  private static void line(StringBuilder sb, String line) {
    sb.append(line).append(CRLF);
  }
}
