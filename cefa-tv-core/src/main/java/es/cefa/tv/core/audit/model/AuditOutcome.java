package es.cefa.tv.core.audit.model;

/** Resultado de una acción auditada. */
public enum AuditOutcome {
  /** La acción se completó. */
  SUCCESS,
  /** La acción se rechazó por falta de sesión, de permiso o de credenciales válidas. */
  DENIED,
  /** La acción falló por un error de validación, un conflicto o un fallo interno. */
  FAILED
}
