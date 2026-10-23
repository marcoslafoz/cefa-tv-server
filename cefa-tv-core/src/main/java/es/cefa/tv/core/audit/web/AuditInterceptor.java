package es.cefa.tv.core.audit.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.audit.model.AuditOutcome;
import es.cefa.tv.core.audit.service.AuditClient;
import es.cefa.tv.core.audit.service.AuditDiff;
import es.cefa.tv.core.audit.service.AuditEvent;
import es.cefa.tv.core.audit.service.AuditSanitizer;
import es.cefa.tv.core.audit.service.AuditService;
import es.cefa.tv.core.audit.snapshot.AuditSnapshots;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.shared.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Registra en la auditoría, de forma central, toda operación de escritura de la API de
 * administración y todo acceso denegado, sin que los controladores tengan que ocuparse de ello. Lee
 * el estado de la entidad antes y después para resumir los cambios.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
  private static final String API_PREFIX = "/api/v1/";
  private static final String LOGIN_ACTION = "AUTH_LOGIN";
  private static final String ACCESS_DENIED = "ACCESS_DENIED";
  private static final Map<String, String> PLAYER_FIELD_ACTIONS =
      Map.of(
          "preferredOutput", "PLAYER_OUTPUT_CHANGE",
          "volume", "PLAYER_VOLUME_CHANGE",
          "site", "PLAYER_SITE_ASSIGN",
          "idleLogo", "PLAYER_IDLE_LOGO_CHANGE");
  private static final int MAX_UPLOAD_ENTRIES = 50;
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final AuditService audit;
  private final AuditSnapshots snapshots;
  private final AuditSanitizer sanitizer;
  private final ObjectMapper objectMapper;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!isApiHandler(request, handler) || SAFE_METHODS.contains(request.getMethod())) {
      return true;
    }
    try {
      AuditActionResolver.Resolution resolution = resolve(request);
      UUID id = pathId(request);
      if (id != null && snapshots.supports(resolution.entityType())) {
        request.setAttribute(AuditAttributes.BEFORE, snapshots.read(resolution.entityType(), id));
      }
    } catch (RuntimeException e) {
      log.warn("No se pudo leer el estado previo para la auditoría", e);
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (!isApiHandler(request, handler)) {
      return;
    }
    try {
      audit(request, response, ex);
    } catch (RuntimeException e) {
      log.warn("No se pudo auditar la petición {}", request.getRequestURI(), e);
    }
  }

  /** Construye y registra la entrada de una petición ya terminada. */
  private void audit(HttpServletRequest request, HttpServletResponse response, Exception ex) {
    String method = request.getMethod();
    int status = ex != null && response.getStatus() < 400 ? 500 : response.getStatus();
    boolean denied = status == 401 || status == 403;
    if (SAFE_METHODS.contains(method) && !denied) {
      return;
    }
    AuditActionResolver.Resolution resolution = resolve(request);
    if (SAFE_METHODS.contains(method)) {
      resolution = new AuditActionResolver.Resolution(ACCESS_DENIED, resolution.entityType());
    }
    Object responseBody = request.getAttribute(AuditAttributes.RESPONSE_BODY);
    Map<String, Object> requestBody =
        sanitizer.body(request.getAttribute(AuditAttributes.REQUEST_BODY));
    Actor actor = actor(resolution, responseBody, requestBody, status);
    if (actor == null) {
      return;
    }
    AuditOutcome outcome =
        status < 400 ? AuditOutcome.SUCCESS : denied ? AuditOutcome.DENIED : AuditOutcome.FAILED;
    AuditClient client = AuditClient.of(request);
    if (outcome == AuditOutcome.SUCCESS
        && responseBody instanceof List<?> list
        && "MEDIA_UPLOAD".equals(resolution.action())) {
      recordUploads(actor, client, resolution, list, method, request);
      return;
    }
    recordOne(actor, client, resolution, request, outcome, status, responseBody, requestBody);
  }

  /** Registra una entrada por cada medio subido. */
  private void recordUploads(
      Actor actor,
      AuditClient client,
      AuditActionResolver.Resolution resolution,
      List<?> uploaded,
      String method,
      HttpServletRequest request) {
    int count = 0;
    for (Object item : uploaded) {
      if (count++ >= MAX_UPLOAD_ENTRIES) {
        break;
      }
      Map<String, Object> media = asMap(item);
      Map<String, Object> details = baseDetails(request, method, 200);
      details.put("type", media.get("type"));
      details.put("sizeBytes", media.get("sizeBytes"));
      audit.record(
          new AuditEvent(
              actor.id(),
              actor.username(),
              resolution.action(),
              resolution.entityType(),
              text(media.get("id")),
              text(media.get("name")),
              AuditOutcome.SUCCESS,
              details,
              client.ip(),
              client.userAgent()));
    }
  }

  /** Registra la entrada de una petición con su entidad, cambios y motivo de error. */
  private void recordOne(
      Actor actor,
      AuditClient client,
      AuditActionResolver.Resolution resolution,
      HttpServletRequest request,
      AuditOutcome outcome,
      int status,
      Object responseBody,
      Map<String, Object> requestBody) {
    @SuppressWarnings("unchecked")
    Map<String, Object> before = (Map<String, Object>) request.getAttribute(AuditAttributes.BEFORE);
    Map<String, Object> bodyMap =
        responseBody == null || responseBody instanceof List<?> ? Map.of() : asMap(responseBody);
    String entityId = entityId(request, resolution, actor, bodyMap);
    Map<String, Object> after = null;
    if (outcome == AuditOutcome.SUCCESS
        && !resolution.isDelete()
        && snapshots.supports(resolution.entityType())) {
      UUID uuid = uuid(entityId);
      after = uuid == null ? null : snapshots.read(resolution.entityType(), uuid);
    }
    Map<String, Object> details = baseDetails(request, request.getMethod(), status);
    if (responseBody instanceof ErrorResponse error) {
      details.put("errorCode", error.errorCode());
      details.put("error", error.message());
    }
    List<Map<String, Object>> changes = AuditDiff.changes(before, after);
    String action = resolution.action();
    if (!changes.isEmpty()) {
      details.put("changes", changes);
    }
    if (resolution.isDelete() && before != null) {
      details.put("state", before);
    } else if (resolution.isCreate() && after != null) {
      details.put("state", after);
    } else if (before == null && after == null && requestBody != null && !requestBody.isEmpty()) {
      details.put("request", requestBody);
    }
    String name = entityName(after, before, bodyMap, actor, resolution);
    if (name == null && "SETTINGS".equals(resolution.entityType())) {
      name = AuditActionResolver.relative(pattern(request)).replaceFirst("^settings/", "");
    }
    if (outcome == AuditOutcome.SUCCESS && "FOLDER_UPDATE".equals(action)) {
      action = folderAction(changes);
    }
    if (outcome == AuditOutcome.SUCCESS && "PLAYER_UPDATE".equals(action)) {
      for (Map.Entry<String, String> specific : PLAYER_FIELD_ACTIONS.entrySet()) {
        List<Map<String, Object>> matching =
            changes.stream()
                .filter(change -> specific.getKey().equals(change.get("field")))
                .toList();
        if (matching.isEmpty()) {
          continue;
        }
        if (changes.size() == 1) {
          action = specific.getValue();
        } else {
          recordFieldChange(
              actor, client, resolution, entityId, name, specific.getValue(), matching);
        }
      }
    }
    audit.record(
        new AuditEvent(
            actor.id(),
            actor.username(),
            action,
            resolution.entityType(),
            entityId,
            name,
            outcome,
            details,
            client.ip(),
            client.userAgent()));
  }

  /** Distingue mover de renombrar una carpeta según los campos que cambiaron. */
  private static String folderAction(List<Map<String, Object>> changes) {
    boolean moved = changes.stream().anyMatch(change -> "parent".equals(change.get("field")));
    boolean renamed = changes.stream().anyMatch(change -> "name".equals(change.get("field")));
    if (moved) {
      return "FOLDER_MOVE";
    }
    return renamed ? "FOLDER_RENAME" : "FOLDER_UPDATE";
  }

  /** Registra aparte un cambio concreto de un reproductor cuando va junto a otros cambios. */
  private void recordFieldChange(
      Actor actor,
      AuditClient client,
      AuditActionResolver.Resolution resolution,
      String entityId,
      String name,
      String action,
      List<Map<String, Object>> output) {
    audit.record(
        new AuditEvent(
            actor.id(),
            actor.username(),
            action,
            resolution.entityType(),
            entityId,
            name,
            AuditOutcome.SUCCESS,
            Map.of("changes", output),
            client.ip(),
            client.userAgent()));
  }

  /** Detalle común: método, ruta y estado. */
  private Map<String, Object> baseDetails(HttpServletRequest request, String method, int status) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("method", method);
    details.put("path", AuditActionResolver.relative(pattern(request)));
    details.put("status", status);
    return details;
  }

  /** Determina quién realizó la acción, o {@code null} si no hay nadie que auditar. */
  private Actor actor(
      AuditActionResolver.Resolution resolution,
      Object responseBody,
      Map<String, Object> requestBody,
      int status) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      return new Actor(user.id(), user.username());
    }
    if (!LOGIN_ACTION.equals(resolution.action())) {
      return null;
    }
    if (status < 400 && responseBody != null) {
      Map<String, Object> user = asMap(responseBody);
      return new Actor(uuid(text(user.get("id"))), text(user.get("username")));
    }
    Object username = requestBody == null ? null : requestBody.get("username");
    return new Actor(null, username == null ? null : text(username));
  }

  /** Obtiene el identificador de la entidad afectada. */
  private String entityId(
      HttpServletRequest request,
      AuditActionResolver.Resolution resolution,
      Actor actor,
      Map<String, Object> bodyMap) {
    UUID path = pathId(request);
    if (path != null) {
      return path.toString();
    }
    if ("USER".equals(resolution.entityType()) && resolution.action().startsWith("AUTH_")) {
      return actor.id() == null ? null : actor.id().toString();
    }
    return text(bodyMap.get("id"));
  }

  /** Obtiene el nombre legible de la entidad. */
  private String entityName(
      Map<String, Object> after,
      Map<String, Object> before,
      Map<String, Object> bodyMap,
      Actor actor,
      AuditActionResolver.Resolution resolution) {
    for (Map<String, Object> source : java.util.Arrays.asList(after, before, bodyMap)) {
      String name = source == null ? null : firstText(source, "name", "username");
      if (name != null) {
        return name;
      }
    }
    return resolution.action().startsWith("AUTH_") ? actor.username() : null;
  }

  /** Devuelve el primer valor no vacío entre varias claves. */
  private static String firstText(Map<String, Object> source, String... keys) {
    for (String key : keys) {
      String value = text(source.get(key));
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  /** Indica si la petición va a un endpoint de la API de administración. */
  private static boolean isApiHandler(HttpServletRequest request, Object handler) {
    return handler instanceof HandlerMethod && request.getRequestURI().startsWith(API_PREFIX);
  }

  /** Resuelve la acción de la petición. */
  private AuditActionResolver.Resolution resolve(HttpServletRequest request) {
    String contentType = request.getContentType();
    boolean multipart = contentType != null && contentType.startsWith("multipart/");
    return AuditActionResolver.resolve(request.getMethod(), pattern(request), multipart);
  }

  /** Obtiene la plantilla de ruta que atendió la petición. */
  private static String pattern(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern == null ? request.getRequestURI() : pattern.toString();
  }

  /** Lee la variable de ruta {@code id} como UUID. */
  @SuppressWarnings("unchecked")
  private static UUID pathId(HttpServletRequest request) {
    Map<String, String> variables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    return variables == null ? null : uuid(variables.get("id"));
  }

  /** Convierte un texto en UUID, o {@code null} si no lo es. */
  private static UUID uuid(String value) {
    if (value == null) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Convierte un valor en texto, o {@code null}. */
  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  /** Convierte un objeto en mapa sin sanear; solo se leen de él identificadores y nombres. */
  private Map<String, Object> asMap(Object value) {
    try {
      return new LinkedHashMap<>(objectMapper.convertValue(value, MAP_TYPE));
    } catch (IllegalArgumentException e) {
      return new LinkedHashMap<>();
    }
  }

  /** Autor de una acción. */
  private record Actor(UUID id, String username) {}
}
