package es.cefa.tv.core.audit.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el interceptor de auditoría antes que el de permisos, de modo que también quedan
 * registrados los accesos denegados.
 */
@Configuration
@RequiredArgsConstructor
public class AuditWebConfig implements WebMvcConfigurer {

  private static final int ORDER_BEFORE_PERMISSIONS = -100;

  private final AuditInterceptor auditInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(auditInterceptor)
        .addPathPatterns("/api/v1/**")
        .order(ORDER_BEFORE_PERMISSIONS);
  }
}
