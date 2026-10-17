package es.cefa.tv.core.audit.snapshot;

import java.util.Map;
import java.util.UUID;

/**
 * Fuente del estado legible de un tipo de entidad, que se lee antes y después de una operación para
 * calcular qué cambió. Los valores son textos, números, booleanos o listas de textos, nunca
 * secretos.
 */
public interface AuditSnapshotSource {

  /**
   * Tipo de entidad que describe, el mismo que se guarda en la auditoría.
   *
   * @return el tipo de entidad
   */
  String entityType();

  /**
   * Lee el estado actual de una entidad.
   *
   * @param id identificador
   * @return los campos auditables, o {@code null} si la entidad no existe
   */
  Map<String, Object> snapshot(UUID id);
}
