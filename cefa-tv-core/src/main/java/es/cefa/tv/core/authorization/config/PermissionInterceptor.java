package es.cefa.tv.core.authorization.config;

import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.annotation.RequiresScope;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.shared.exception.ApiException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** Aplica {@link RequiresPermission} y {@link RequiresScope} antes de ejecutar cada endpoint. */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

  private final AccessService access;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method)
        || request.getDispatcherType() == DispatcherType.ASYNC) {
      return true;
    }
    RequiresPermission permission = find(method, RequiresPermission.class);
    RequiresScope scope = find(method, RequiresScope.class);
    if (permission == null && scope == null) {
      return true;
    }
    AuthenticatedUser user = access.current();
    if (permission != null) {
      access.requireAnyPermission(user, permission.value());
    }
    if (scope != null) {
      UUID id = identifier(request, scope.variable());
      if (scope.target() == RequiresScope.Target.PLAYER) {
        access.requirePlayerLevel(id, scope.level());
      } else {
        access.requireGroupLevel(id, scope.level());
      }
    }
    return true;
  }

  /** Busca la anotación en el método y, si no está, en la clase. */
  private <A extends java.lang.annotation.Annotation> A find(HandlerMethod method, Class<A> type) {
    A onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), type);
    return onMethod != null
        ? onMethod
        : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), type);
  }

  /** Lee el identificador de la variable de ruta o del parámetro de consulta. */
  @SuppressWarnings("unchecked")
  private UUID identifier(HttpServletRequest request, String variable) {
    Map<String, String> pathVariables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    String raw = pathVariables == null ? null : pathVariables.get(variable);
    if (raw == null) {
      raw = request.getParameter(variable);
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw ApiException.badRequest("INVALID_ID", "Identificador no válido");
    }
  }
}
