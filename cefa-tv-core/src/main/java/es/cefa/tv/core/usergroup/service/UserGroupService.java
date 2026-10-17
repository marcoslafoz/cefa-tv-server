package es.cefa.tv.core.usergroup.service;

import es.cefa.tv.core.auth.repository.UserRepository;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AuthenticatedUserLoader;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.usergroup.dto.UserGroupRequest;
import es.cefa.tv.core.usergroup.dto.UserGroupResponse;
import es.cefa.tv.core.usergroup.model.UserGroup;
import es.cefa.tv.core.usergroup.repository.AccessGrantRepository;
import es.cefa.tv.core.usergroup.repository.UserGroupRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de grupos de usuarios: sus permisos, su acceso total, sus miembros y sus concesiones
 * sobre grupos de reproductores y sedes.
 */
@Service
@RequiredArgsConstructor
public class UserGroupService {

  private final UserGroupRepository userGroups;
  private final AccessGrantRepository grants;
  private final UserRepository users;
  private final GrantMapper grantMapper;
  private final AuthenticatedUserLoader loader;
  private final AdminGuard adminGuard;
  private final GroupPrivilegeGuard privilegeGuard;

  /**
   * Lista los grupos de usuarios.
   *
   * @return los grupos ordenados por nombre
   */
  @Transactional(readOnly = true)
  public List<UserGroupResponse> list() {
    return userGroups.findAll().stream()
        .sorted(Comparator.comparing(UserGroup::getName, String.CASE_INSENSITIVE_ORDER))
        .map(this::toResponse)
        .toList();
  }

  /**
   * Obtiene un grupo de usuarios.
   *
   * @param id identificador
   * @return el grupo
   */
  @Transactional(readOnly = true)
  public UserGroupResponse get(UUID id) {
    return toResponse(find(id));
  }

  /**
   * Crea un grupo de usuarios.
   *
   * @param request datos
   * @return el grupo creado
   */
  @Transactional
  public UserGroupResponse create(UserGroupRequest request) {
    if (userGroups.existsByNameIgnoreCase(request.name().trim())) {
      throw ApiException.conflict("USER_GROUP_NAME_EXISTS", "Ya existe un grupo con ese nombre");
    }
    boolean allAccess = Boolean.TRUE.equals(request.allAccess());
    Set<Permission> permissions = parsePermissions(request.permissions(), allAccess);
    privilegeGuard.requireCanDefine(permissions, allAccess);
    UserGroup group = new UserGroup();
    group.setId(UUID.randomUUID());
    group.setName(request.name().trim());
    group.setDescription(request.description());
    group.setAllAccess(allAccess);
    group.setPermissions(names(permissions));
    group.setMemberIds(new HashSet<>(memberIds(request)));
    if (!group.getMemberIds().isEmpty()) {
      privilegeGuard.requireCanAssign(group);
    }
    userGroups.saveAndFlush(group);
    grants.saveAll(grantMapper.toGrants(request.grants(), group.getId()));
    loader.invalidateAllAfterCommit();
    return toResponse(group);
  }

  /**
   * Modifica un grupo de usuarios. Un grupo integrado solo admite cambiar sus miembros.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return el grupo actualizado
   */
  @Transactional
  public UserGroupResponse update(UUID id, UserGroupRequest request) {
    UserGroup group = find(id);
    boolean allAccess = request.allAccess() == null ? group.isAllAccess() : request.allAccess();
    Set<Permission> permissions =
        request.permissions() == null && !allAccess
            ? group.permissionSet()
            : parsePermissions(request.permissions(), allAccess);
    String name = request.name().trim();
    if (group.isBuiltin()) {
      requireUnchangedBuiltin(group, name, permissions, allAccess);
    } else if (!group.getName().equalsIgnoreCase(name) && userGroups.existsByNameIgnoreCase(name)) {
      throw ApiException.conflict("USER_GROUP_NAME_EXISTS", "Ya existe un grupo con ese nombre");
    }
    privilegeGuard.requireCanDefine(permissions, allAccess);
    privilegeGuard.requireCanAssign(group);
    adminGuard.run(
        () -> {
          if (!group.isBuiltin()) {
            group.setName(name);
            group.setDescription(request.description());
            group.setAllAccess(allAccess);
            group.setPermissions(names(permissions));
          }
          if (request.memberIds() != null) {
            group.setMemberIds(new HashSet<>(memberIds(request)));
          }
          if (request.grants() != null) {
            grants.deleteByUserGroupId(group.getId());
            grants.flush();
            grants.saveAll(grantMapper.toGrants(request.grants(), group.getId()));
          }
        });
    loader.invalidateAllAfterCommit();
    return toResponse(group);
  }

  /**
   * Borra un grupo de usuarios con sus concesiones; los integrados no se pueden borrar.
   *
   * @param id identificador
   */
  @Transactional
  public void delete(UUID id) {
    UserGroup group = find(id);
    if (group.isBuiltin()) {
      throw ApiException.conflict(
          "USER_GROUP_BUILTIN", "Los grupos integrados no se pueden modificar ni borrar");
    }
    privilegeGuard.requireCanAssign(group);
    adminGuard.run(() -> userGroups.delete(group));
    loader.invalidateAllAfterCommit();
  }

  /** Rechaza cambiar nada de un grupo integrado salvo sus miembros. */
  private void requireUnchangedBuiltin(
      UserGroup group, String name, Set<Permission> permissions, boolean allAccess) {
    if (!group.getName().equals(name)
        || allAccess != group.isAllAccess()
        || !permissions.equals(group.permissionSet())) {
      throw ApiException.conflict(
          "USER_GROUP_BUILTIN", "Los grupos integrados no se pueden modificar ni borrar");
    }
  }

  /** Valida los permisos pedidos; un grupo con acceso total los tiene todos. */
  private static Set<Permission> parsePermissions(List<String> requested, boolean allAccess) {
    if (allAccess) {
      return EnumSet.allOf(Permission.class);
    }
    Set<Permission> permissions = EnumSet.noneOf(Permission.class);
    for (String name : requested == null ? List.<String>of() : requested) {
      try {
        permissions.add(Permission.valueOf(name));
      } catch (IllegalArgumentException e) {
        throw ApiException.badRequest("UNKNOWN_PERMISSION", "Permiso desconocido: " + name);
      }
    }
    return permissions;
  }

  /** Convierte los permisos en el formato de almacenamiento, ordenados. */
  private static String[] names(Set<Permission> permissions) {
    return permissions.stream().map(Enum::name).sorted().toArray(String[]::new);
  }

  /** Valida que los miembros pedidos existan. */
  private List<UUID> memberIds(UserGroupRequest request) {
    List<UUID> memberIds =
        request.memberIds() == null ? List.of() : request.memberIds().stream().distinct().toList();
    if (users.findAllById(memberIds).size() != memberIds.size()) {
      throw ApiException.badRequest("USER_NOT_FOUND", "Usuario inexistente");
    }
    return memberIds;
  }

  /** Busca un grupo o lanza un 404. */
  private UserGroup find(UUID id) {
    return userGroups.findById(id).orElseThrow(() -> ApiException.notFound("Grupo de usuarios"));
  }

  /** Convierte un grupo en su DTO. */
  private UserGroupResponse toResponse(UserGroup group) {
    return new UserGroupResponse(
        group.getId(),
        group.getName(),
        group.getDescription(),
        group.isBuiltin(),
        group.isAllAccess(),
        Arrays.stream(group.getPermissions()).sorted().toList(),
        group.getMemberIds().stream().sorted().toList(),
        group.getMemberIds().size(),
        grantMapper.toDtos(grants.findByUserGroupId(group.getId())));
  }
}
