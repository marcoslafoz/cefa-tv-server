package es.cefa.tv.core.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.audit.dto.AuditActionResponse;
import es.cefa.tv.core.audit.dto.AuditFilter;
import es.cefa.tv.core.audit.dto.AuditLogResponse;
import es.cefa.tv.core.audit.dto.AuditStatsResponse;
import es.cefa.tv.core.audit.model.AuditLog;
import es.cefa.tv.core.audit.repository.AuditLogRepository;
import es.cefa.tv.core.shared.dto.PageResponse;
import es.cefa.tv.core.shared.exception.ApiException;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta, exportación y estadísticas del registro de auditoría. */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

  /** Tamaño de página máximo en las consultas. */
  public static final int MAX_PAGE_SIZE = 200;

  /** Número máximo de filas de una exportación. */
  public static final int EXPORT_MAX_ROWS = 50_000;

  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int EXPORT_PAGE_SIZE = 500;
  private static final int TOP_LIMIT = 10;
  private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);
  private static final String[] CSV_HEADER = {
    "at", "username", "action", "entityType", "entityId", "entityName", "outcome", "ip", "details"
  };

  private final AuditLogRepository logs;
  private final ObjectMapper objectMapper;

  /**
   * Busca entradas con filtros, de la más reciente a la más antigua.
   *
   * @param filter filtros
   * @param page número de página, desde 0
   * @param size tamaño de página, limitado a {@value #MAX_PAGE_SIZE}
   * @return la página de entradas
   */
  @Transactional(readOnly = true)
  public PageResponse<AuditLogResponse> search(AuditFilter filter, int page, int size) {
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    Pageable pageable =
        PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "at"));
    Page<AuditLog> result = logs.findAll(specification(filter), pageable);
    return PageResponse.of(result, AuditQueryService::toResponse);
  }

  /**
   * Obtiene una entrada.
   *
   * @param id identificador
   * @return la entrada
   */
  @Transactional(readOnly = true)
  public AuditLogResponse get(UUID id) {
    return logs.findById(id)
        .map(AuditQueryService::toResponse)
        .orElseThrow(() -> ApiException.notFound("Entrada de auditoría"));
  }

  /**
   * Escribe en CSV las entradas que cumplen el filtro, de la más reciente a la más antigua, hasta
   * {@value #EXPORT_MAX_ROWS} filas.
   *
   * @param filter filtros
   * @param out destino de la exportación
   * @throws IOException si falla la escritura
   */
  public void exportCsv(AuditFilter filter, Writer out) throws IOException {
    out.write('﻿');
    writeRow(out, List.of(CSV_HEADER));
    Specification<AuditLog> specification = specification(filter);
    int written = 0;
    for (int page = 0; written < EXPORT_MAX_ROWS; page++) {
      Page<AuditLog> chunk =
          logs.findAll(
              specification,
              PageRequest.of(page, EXPORT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "at")));
      for (AuditLog entry : chunk.getContent()) {
        if (written++ >= EXPORT_MAX_ROWS) {
          return;
        }
        writeRow(out, csvRow(entry));
      }
      if (!chunk.hasNext()) {
        return;
      }
    }
  }

  /**
   * Resume la actividad de un intervalo.
   *
   * @param from inicio; por defecto, hace siete días
   * @param to fin; por defecto, ahora
   * @return las estadísticas
   */
  @Transactional(readOnly = true)
  public AuditStatsResponse stats(Instant from, Instant to) {
    Instant end = to == null ? Instant.now() : to;
    Instant start = from == null ? end.minus(DEFAULT_WINDOW) : from;
    Pageable top = PageRequest.ofSize(TOP_LIMIT);
    Map<String, Long> byOutcome = new LinkedHashMap<>();
    logs.countByOutcome(start, end).forEach(row -> byOutcome.put(row.getOutcome(), row.getTotal()));
    return new AuditStatsResponse(
        start,
        end,
        logs.countBetween(start, end),
        byOutcome,
        logs.topActions(start, end, top).stream()
            .map(row -> new AuditStatsResponse.ActionStat(row.getAction(), row.getTotal()))
            .toList(),
        logs.topUsers(start, end, top).stream()
            .map(
                row ->
                    new AuditStatsResponse.UserStat(
                        row.getUserId(), row.getUsername(), row.getTotal()))
            .toList(),
        logs.countByDay(start, end).stream()
            .map(row -> new AuditStatsResponse.DayStat(row.getDay(), row.getTotal()))
            .toList());
  }

  /**
   * Devuelve las acciones para el filtro: las del catálogo y las que consten en el registro.
   *
   * @return las acciones ordenadas por nombre
   */
  @Transactional(readOnly = true)
  public List<AuditActionResponse> actions() {
    Map<String, AuditActionResponse> actions = new LinkedHashMap<>();
    AuditActionCatalog.KNOWN.forEach(action -> actions.put(action.action(), action));
    Set<String> recorded = new LinkedHashSet<>(logs.distinctActions());
    recorded.forEach(
        name -> actions.computeIfAbsent(name, key -> new AuditActionResponse(key, null)));
    return actions.values().stream()
        .sorted(java.util.Comparator.comparing(AuditActionResponse::action))
        .toList();
  }

  /** Construye la condición de búsqueda a partir de los filtros. */
  private Specification<AuditLog> specification(AuditFilter filter) {
    return (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (filter.from() != null) {
        predicates.add(builder.greaterThanOrEqualTo(root.get("at"), filter.from()));
      }
      if (filter.to() != null) {
        predicates.add(builder.lessThan(root.get("at"), filter.to()));
      }
      if (filter.userId() != null) {
        predicates.add(builder.equal(root.get("userId"), filter.userId()));
      }
      if (hasText(filter.action())) {
        predicates.add(builder.equal(root.get("action"), filter.action()));
      }
      if (hasText(filter.entityType())) {
        predicates.add(builder.equal(root.get("entityType"), filter.entityType()));
      }
      if (hasText(filter.entityId())) {
        predicates.add(builder.equal(root.get("entityId"), filter.entityId()));
      }
      if (filter.outcome() != null) {
        predicates.add(builder.equal(root.get("outcome"), filter.outcome()));
      }
      if (hasText(filter.q())) {
        String pattern = "%" + escapeLike(filter.q().trim().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            builder.or(
                builder.like(builder.lower(root.get("username")), pattern, '\\'),
                builder.like(builder.lower(root.get("action")), pattern, '\\'),
                builder.like(builder.lower(root.get("entityName")), pattern, '\\'),
                builder.like(builder.lower(root.get("entityId")), pattern, '\\')));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }

  /** Escapa los comodines de LIKE para buscar el texto literal. */
  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /** Indica si el texto tiene contenido. */
  private static boolean hasText(String text) {
    return text != null && !text.isBlank();
  }

  /** Convierte una entrada en su DTO. */
  private static AuditLogResponse toResponse(AuditLog entry) {
    return new AuditLogResponse(
        entry.getId(),
        entry.getAt(),
        entry.getUserId(),
        entry.getUsername(),
        entry.getAction(),
        entry.getEntityType(),
        entry.getEntityId(),
        entry.getEntityName(),
        entry.getOutcome(),
        entry.getDetails(),
        entry.getIp(),
        entry.getUserAgent());
  }

  /** Construye las celdas CSV de una entrada. */
  private List<String> csvRow(AuditLog entry) {
    return List.of(
        entry.getAt().toString(),
        nullToEmpty(entry.getUsername()),
        entry.getAction(),
        nullToEmpty(entry.getEntityType()),
        nullToEmpty(entry.getEntityId()),
        nullToEmpty(entry.getEntityName()),
        entry.getOutcome().name(),
        nullToEmpty(entry.getIp()),
        detailsJson(entry));
  }

  /** Serializa el detalle de una entrada. */
  private String detailsJson(AuditLog entry) {
    if (entry.getDetails() == null) {
      return "";
    }
    try {
      return objectMapper.writeValueAsString(entry.getDetails());
    } catch (JsonProcessingException e) {
      return "";
    }
  }

  /** Escribe una fila CSV neutralizando las fórmulas de hoja de cálculo. */
  private static void writeRow(Writer out, List<String> cells) throws IOException {
    StringBuilder row = new StringBuilder();
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        row.append(',');
      }
      row.append(csvCell(cells.get(i)));
    }
    row.append("\r\n");
    out.write(row.toString());
  }

  /** Escapa una celda CSV y antepone una comilla a las que empiezan por un operador de fórmula. */
  private static String csvCell(String value) {
    String safe = value;
    if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
      safe = "'" + safe;
    }
    if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
      return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
    return safe;
  }

  /** Sustituye un nulo por una cadena vacía. */
  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
