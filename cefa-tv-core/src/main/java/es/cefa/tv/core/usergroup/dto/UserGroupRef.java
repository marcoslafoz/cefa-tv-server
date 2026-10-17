package es.cefa.tv.core.usergroup.dto;

import java.util.UUID;

/**
 * Referencia a un grupo de usuarios.
 *
 * @param id identificador
 * @param name nombre
 */
public record UserGroupRef(UUID id, String name) {}
