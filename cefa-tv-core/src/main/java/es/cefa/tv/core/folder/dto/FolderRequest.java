package es.cefa.tv.core.folder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Creación de una carpeta de la biblioteca de medios.
 *
 * @param name nombre, único entre las carpetas hermanas sin distinguir mayúsculas
 * @param parentId carpeta padre, o {@code null} para crearla en la raíz
 */
public record FolderRequest(@NotBlank @Size(max = 150) String name, UUID parentId) {}
