package es.cefa.tv.core.authorization.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra el interceptor de permisos en las rutas de la API de administración. */
@Configuration
@RequiredArgsConstructor
public class AuthorizationWebConfig implements WebMvcConfigurer {

  private final PermissionInterceptor permissionInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/v1/**");
  }
}
