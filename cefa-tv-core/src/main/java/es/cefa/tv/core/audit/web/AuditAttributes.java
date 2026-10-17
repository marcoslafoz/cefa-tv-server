package es.cefa.tv.core.audit.web;

/** Nombres de los atributos de petición con los que se comparte el contexto de auditoría. */
final class AuditAttributes {

  /** Estado de la entidad antes de la operación. */
  static final String BEFORE = AuditAttributes.class.getName() + ".before";

  /** Cuerpo de la petición ya deserializado. */
  static final String REQUEST_BODY = AuditAttributes.class.getName() + ".requestBody";

  /** Cuerpo de la respuesta antes de escribirse. */
  static final String RESPONSE_BODY = AuditAttributes.class.getName() + ".responseBody";

  /** Evita la instanciación de la clase de constantes. */
  private AuditAttributes() {}
}
