package es.cefa.tv.core.auth.dto;

import es.cefa.tv.core.auth.model.AuthProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Modificación de un usuario.
 *
 * @param displayName nombre visible
 * @param email correo; si es {@code null} se conserva el actual y una cadena vacía lo elimina
 * @param authProvider forma de inicio de sesión; si es {@code null} se conserva la actual
 * @param password contraseña de 8 caracteres como mínimo; solo hace falta al pasar de solo
 *     Microsoft a un modo con contraseña y no se admite en usuarios de solo Microsoft
 * @param enabled si queda activo
 * @param groupIds grupos de usuarios a los que pertenece, que sustituyen a los actuales; si es
 *     {@code null} se conservan
 */
public record UserUpdateRequest(
    @NotBlank @Size(max = 150) String displayName,
    @Email @Size(max = 320) String email,
    AuthProvider authProvider,
    @Size(min = 8, max = 100) String password,
    @NotNull Boolean enabled,
    List<UUID> groupIds) {}
