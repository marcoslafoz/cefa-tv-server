package es.cefa.tv.core.microsoft.dto;

/**
 * Formas de iniciar sesión disponibles, para que la interfaz muestre solo las que funcionan.
 *
 * @param local si está disponible el formulario de usuario y contraseña
 * @param microsoft si está configurado el inicio de sesión con Microsoft
 */
public record AuthProvidersResponse(boolean local, boolean microsoft) {}
