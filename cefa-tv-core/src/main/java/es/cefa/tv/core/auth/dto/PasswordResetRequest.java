package es.cefa.tv.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Restablecimiento de la contraseña de un usuario por un administrador.
 *
 * @param password contraseña nueva, de 8 caracteres como mínimo
 */
public record PasswordResetRequest(@NotBlank @Size(min = 8, max = 100) String password) {}
