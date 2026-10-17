package es.cefa.tv.core.authorization.annotation;

import es.cefa.tv.core.authorization.model.AccessLevel;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige que el usuario alcance un nivel sobre el reproductor o el grupo de reproductores que
 * identifica una variable de la ruta o un parámetro de la petición.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresScope {

  /** Elemento sobre el que se comprueba el alcance. */
  enum Target {
    /** La variable identifica un reproductor. */
    PLAYER,
    /** La variable identifica un grupo de reproductores. */
    GROUP
  }

  /**
   * Tipo de elemento que identifica la variable.
   *
   * @return el tipo
   */
  Target target();

  /**
   * Nombre de la variable de ruta o parámetro de consulta con el identificador.
   *
   * @return el nombre
   */
  String variable() default "id";

  /**
   * Nivel exigido.
   *
   * @return el nivel
   */
  AccessLevel level();
}
