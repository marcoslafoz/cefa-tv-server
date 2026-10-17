package es.cefa.tv.core.group.controller;

import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.annotation.RequiresScope;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.group.dto.GroupRequest;
import es.cefa.tv.core.group.dto.GroupResponse;
import es.cefa.tv.core.group.service.GroupService;
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

/** Endpoints de grupos de reproductores. */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

  private final GroupService groupService;

  /**
   * Lista los grupos.
   *
   * @return los grupos
   */
  @RequiresPermission({
    Permission.PLAYER_VIEW,
    Permission.PLAYER_MANAGE,
    Permission.SCHEDULE_MANAGE
  })
  @GetMapping
  public List<GroupResponse> list() {
    return groupService.list();
  }

  /**
   * Crea un grupo.
   *
   * @param request datos del grupo
   * @return el grupo creado
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public GroupResponse create(@Valid @RequestBody GroupRequest request) {
    return groupService.create(request);
  }

  /**
   * Modifica un grupo.
   *
   * @param id identificador
   * @param request nuevos datos
   * @return el grupo actualizado
   */
  @RequiresPermission({Permission.PLAYER_MANAGE, Permission.SCHEDULE_MANAGE})
  @RequiresScope(target = RequiresScope.Target.GROUP, level = AccessLevel.OPERATE)
  @PutMapping("/{id}")
  public GroupResponse update(@PathVariable UUID id, @Valid @RequestBody GroupRequest request) {
    return groupService.update(id, request);
  }

  /**
   * Borra un grupo.
   *
   * @param id identificador
   */
  @RequiresPermission(Permission.PLAYER_MANAGE)
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    groupService.delete(id);
  }
}
