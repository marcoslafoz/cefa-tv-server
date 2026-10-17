package es.cefa.tv.core.shell.model;

/** Motivo por el que termina una sesión de terminal remota. */
public enum ShellCloseReason {
  /** El usuario cerró la terminal desde la interfaz. */
  USER_CLOSE,
  /** El navegador perdió la conexión con el servidor. */
  VIEWER_DISCONNECTED,
  /** El proceso del shell terminó en el reproductor. */
  PROCESS_EXIT,
  /** El reproductor perdió la conexión con el servidor. */
  PLAYER_DISCONNECTED,
  /** No hubo entrada del usuario durante el tiempo máximo de inactividad. */
  IDLE_TIMEOUT,
  /** La sesión alcanzó su duración máxima. */
  MAX_DURATION,
  /** El servidor se reinició con la sesión abierta. */
  SERVER_RESTART,
  /** El reproductor dejó de ser alcanzable al abrir la sesión. */
  PLAYER_UNREACHABLE
}
