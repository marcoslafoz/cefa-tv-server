package es.cefa.tv.core.audit.controller;

import es.cefa.tv.core.audit.dto.AuditActionResponse;
import es.cefa.tv.core.audit.dto.AuditFilter;
import es.cefa.tv.core.audit.dto.AuditLogResponse;
import es.cefa.tv.core.audit.dto.AuditStatsResponse;
import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.audit.service.AuditQueryService;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.shared.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Consulta de la auditoría de la actividad de los usuarios. */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@RequiresPermission(Permission.AUDIT_VIEW)
public class AuditController {

  private static final DateTimeFormatter FILE_STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final AuditQueryService queries;
  private final AuditService audit;
  private final AccessService access;

  /**
   * Busca entradas, de la más reciente a la más antigua.
   *
   * @param from inicio del intervalo, incluido
   * @param to fin del intervalo, excluido
   * @param userId usuario
   * @param action acción exacta
   * @param entityType tipo de entidad
   * @param entityId identificador de la entidad
   * @param outcome resultado
   * @param q texto libre
   * @param page página, desde 0
   * @param size tamaño de página (máximo 200)
   * @return la página de entradas
   */
  @GetMapping
  public PageResponse<AuditLogResponse> search(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) String entityId,
      @RequestParam(required = false) AuditOutcome outcome,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return queries.search(
        new AuditFilter(from, to, userId, action, entityType, entityId, outcome, q), page, size);
  }

  /**
   * Obtiene una entrada.
   *
   * @param id identificador
   * @return la entrada
   */
  @GetMapping("/{id}")
  public AuditLogResponse get(@PathVariable UUID id) {
    return queries.get(id);
  }

  /**
   * Exporta a CSV las entradas que cumplen el filtro, hasta 50 000 filas.
   *
   * @param from inicio del intervalo
   * @param to fin del intervalo
   * @param userId usuario
   * @param action acción exacta
   * @param entityType tipo de entidad
   * @param entityId identificador de la entidad
   * @param outcome resultado
   * @param q texto libre
   * @param request petición, para registrar el origen de la exportación
   * @return el CSV en streaming
   */
  @GetMapping("/export.csv")
  public ResponseEntity<StreamingResponseBody> export(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) String entityId,
      @RequestParam(required = false) AuditOutcome outcome,
      @RequestParam(required = false) String q,
      HttpServletRequest request) {
    AuditFilter filter =
        new AuditFilter(from, to, userId, action, entityType, entityId, outcome, q);
    audit.recordUser(
        access.current(),
        AuditClient.of(request),
        "AUDIT_EXPORT",
        "SETTINGS",
        null,
        "audit",
        AuditOutcome.SUCCESS,
        filterDetails(filter));
    StreamingResponseBody body =
        out -> {
          OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
          queries.exportCsv(filter, writer);
          writer.flush();
        };
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"audit-" + FILE_STAMP.format(Instant.now()) + ".csv\"")
        .body(body);
  }

  /**
   * Resume la actividad de un intervalo.
   *
   * @param from inicio; por defecto, hace siete días
   * @param to fin; por defecto, ahora
   * @return las estadísticas
   */
  @GetMapping("/stats")
  public AuditStatsResponse stats(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    return queries.stats(from, to);
  }

  /**
   * Lista las acciones para el filtro.
   *
   * @return el catálogo de acciones
   */
  @GetMapping("/actions")
  public List<AuditActionResponse> actions() {
    return queries.actions();
  }

  /** Describe los filtros usados en una exportación. */
  private static Map<String, Object> filterDetails(AuditFilter filter) {
    Map<String, Object> details = new LinkedHashMap<>();
    putIfPresent(details, "from", filter.from());
    putIfPresent(details, "to", filter.to());
    putIfPresent(details, "userId", filter.userId());
    putIfPresent(details, "action", filter.action());
    putIfPresent(details, "entityType", filter.entityType());
    putIfPresent(details, "entityId", filter.entityId());
    putIfPresent(details, "outcome", filter.outcome());
    putIfPresent(details, "q", filter.q());
    return details;
  }

  /** Añade el valor al mapa si no es nulo. */
  private static void putIfPresent(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value.toString());
    }
  }
}
