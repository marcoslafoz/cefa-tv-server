package es.cefa.tv.core.schedule.model;

/** Estado de una programación, o de una de sus ocurrencias, en un instante dado. */
public enum ScheduleStatus {
  /** Está dentro de su franja ahora mismo; no implica que gane por prioridad a otra. */
  ACTIVE_NOW,
  /** Aún no ha empezado o tiene emisiones futuras. */
  SCHEDULED,
  /** Ya terminó y no volverá a emitirse. */
  ENDED,
  /** Está deshabilitada y no se emite. */
  DISABLED
}
