package es.cefa.tv.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de la propia contraseña.
 *
 * @param currentPassword contraseña actual
 * @param newPassword contraseña nueva, de 8 caracteres como mínimo
 */
public record PasswordChangeRequest(
    @NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 100) String newPassword) {}
