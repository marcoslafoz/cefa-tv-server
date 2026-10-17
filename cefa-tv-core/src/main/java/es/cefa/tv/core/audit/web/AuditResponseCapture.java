package es.cefa.tv.core.audit.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Guarda el cuerpo de las respuestas a peticiones de escritura para que la auditoría conozca la
 * entidad creada o el motivo de un error.
 */
@ControllerAdvice
public class AuditResponseCapture implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body != null && request instanceof ServletServerHttpRequest servlet) {
      HttpServletRequest http = servlet.getServletRequest();
      if (http.getRequestURI().startsWith("/api/v1/")) {
        http.setAttribute(AuditAttributes.RESPONSE_BODY, body);
      }
    }
    return body;
  }
}
