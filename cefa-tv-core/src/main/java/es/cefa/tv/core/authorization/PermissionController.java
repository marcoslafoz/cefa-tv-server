package es.cefa.tv.core.authorization;

import es.cefa.tv.core.authorization.annotation.RequiresPermission;
import es.cefa.tv.core.authorization.model.Permission;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de permisos que se pueden dar a un grupo de usuarios. */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiresPermission(Permission.USER_MANAGE)
public class PermissionController {

  /**
   * Lista los permisos con su descripción.
   *
   * @return el catálogo de permisos
   */
  @GetMapping
  public List<PermissionInfo> list() {
    return Arrays.stream(Permission.values())
        .map(permission -> new PermissionInfo(permission.name(), permission.getDescription()))
        .toList();
  }

  /**
   * Permiso del catálogo.
   *
   * @param key identificador del permiso
   * @param description descripción legible
   */
  public record PermissionInfo(String key, String description) {}
}
