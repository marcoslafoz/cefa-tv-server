package es.cefa.tv.core.authorization.annotation;

import es.cefa.tv.core.authorization.model.Permission;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Exige que el usuario tenga al menos uno de los permisos indicados para ejecutar el endpoint. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {

  /**
   * Permisos aceptados; basta con tener uno.
   *
   * @return los permisos
   */
  Permission[] value();
}
