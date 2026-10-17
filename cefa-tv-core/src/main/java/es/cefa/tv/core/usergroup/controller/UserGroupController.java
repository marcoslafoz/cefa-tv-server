package es.cefa.tv.core.usergroup.controller;

import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.usergroup.dto.UserGroupRequest;
import es.cefa.tv.core.usergroup.dto.UserGroupResponse;
import es.cefa.tv.core.usergroup.service.UserGroupService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de administración de grupos de usuarios. */
@RestController
@RequestMapping("/api/v1/user-groups")
@RequiredArgsConstructor
@RequiresPermission(Permission.USER_MANAGE)
public class UserGroupController {

  private final UserGroupService userGroupService;

  /**
   * Lista los grupos de usuarios.
   *
   * @return los grupos
   */
  @GetMapping
  public List<UserGroupResponse> list() {
    return userGroupService.list();
  }

  /**
   * Obtiene un grupo de usuarios.
   *
   * @param id identificador
   * @return el grupo
   */
  @GetMapping("/{id}")
  public UserGroupResponse get(@PathVariable UUID id) {
    return userGroupService.get(id);
  }

  /**
   * Crea un grupo de usuarios.
   *
   * @param request datos
   * @return el grupo creado
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserGroupResponse create(@Valid @RequestBody UserGroupRequest request) {
    return userGroupService.create(request);
  }

  /**
   * Modifica un grupo de usuarios.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return el grupo actualizado
   */
  @PutMapping("/{id}")
  public UserGroupResponse update(
      @PathVariable UUID id, @Valid @RequestBody UserGroupRequest request) {
    return userGroupService.update(id, request);
  }

  /**
   * Borra un grupo de usuarios.
   *
   * @param id identificador
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    userGroupService.delete(id);
  }
}
