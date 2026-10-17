package es.cefa.tv.core.pairing.model;

/** Estado de una petición de emparejamiento. */
public enum PairingStatus {
  /** Esperando que un usuario introduzca el código. */
  PENDING,
  /** Aprobada: el reproductor existe y todavía no ha recogido su token. */
  APPROVED,
  /** El reproductor ya se ha autenticado con su token. */
  CONSUMED,
  /** Caducada o sustituida por un código más reciente. */
  EXPIRED
}
