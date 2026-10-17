package es.cefa.tv.core.audit.web;

import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Guarda el cuerpo de cada petición deserializado para que la auditoría lo describa. */
@ControllerAdvice
public class AuditBodyCapture extends RequestBodyAdviceAdapter {

  @Override
  public boolean supports(
      MethodParameter methodParameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object afterBodyRead(
      Object body,
      HttpInputMessage inputMessage,
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null && body != null) {
      attributes.setAttribute(AuditAttributes.REQUEST_BODY, body, RequestAttributes.SCOPE_REQUEST);
    }
    return body;
  }
}
