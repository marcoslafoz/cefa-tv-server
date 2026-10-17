package es.cefa.tv.core.audit.snapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Registro de las fuentes de estado por tipo de entidad. */
@Component
public class AuditSnapshots {

  private final Map<String, AuditSnapshotSource> sources = new HashMap<>();

  /**
   * Crea el registro con todas las fuentes disponibles.
   *
   * @param available fuentes de estado
   */
  public AuditSnapshots(List<AuditSnapshotSource> available) {
    available.forEach(source -> sources.put(source.entityType(), source));
  }

  /**
   * Indica si hay fuente para un tipo de entidad.
   *
   * @param entityType tipo de entidad
   * @return {@code true} si se puede leer su estado
   */
  public boolean supports(String entityType) {
    return sources.containsKey(entityType);
  }

  /**
   * Lee el estado de una entidad; un fallo de lectura no interrumpe la auditoría.
   *
   * @param entityType tipo de entidad
   * @param id identificador
   * @return el estado, o {@code null} si no hay fuente, no existe o falla la lectura
   */
  public Map<String, Object> read(String entityType, UUID id) {
    AuditSnapshotSource source = sources.get(entityType);
    if (source == null) {
      return null;
    }
    try {
      return source.snapshot(id);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
