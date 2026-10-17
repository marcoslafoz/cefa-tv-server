package es.cefa.tv.core.auth.dto;

import es.cefa.tv.core.auth.model.AuthProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Creación de un usuario.
 *
 * @param username nombre de usuario (3 a 100 caracteres: letras, números, punto, guion y arroba)
 * @param displayName nombre visible
 * @param password contraseña inicial de 8 caracteres como mínimo; obligatoria salvo en usuarios de
 *     solo Microsoft, donde no se admite
 * @param email correo, obligatorio si el usuario puede entrar con Microsoft
 * @param authProvider forma de inicio de sesión: LOCAL, MICROSOFT o HYBRID (LOCAL por defecto)
 * @param enabled si queda activo, por defecto sí
 * @param groupIds grupos de usuarios a los que pertenece; de ellos salen sus permisos y accesos
 */
public record UserCreateRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._@-]{3,100}") String username,
    @NotBlank @Size(max = 150) String displayName,
    @Size(min = 8, max = 100) String password,
    @Email @Size(max = 320) String email,
    AuthProvider authProvider,
    Boolean enabled,
    List<UUID> groupIds) {}
