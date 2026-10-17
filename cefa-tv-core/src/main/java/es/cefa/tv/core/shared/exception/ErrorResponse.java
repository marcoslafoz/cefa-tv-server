package es.cefa.tv.core.shared.exception;

import java.util.Map;

/**
 * Cuerpo estándar de error de la API.
 *
 * @param status código HTTP
 * @param errorCode código de error estable para el cliente
 * @param message descripción legible
 * @param details motivos por campo
 */
public record ErrorResponse(
    int status, String errorCode, String message, Map<String, String> details) {}
