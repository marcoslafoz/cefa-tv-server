package es.cefa.tv.core.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Petición de inicio de sesión.
 *
 * @param username usuario
 * @param password contraseña
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
