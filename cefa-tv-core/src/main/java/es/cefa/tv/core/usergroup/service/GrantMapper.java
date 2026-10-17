package es.cefa.tv.core.usergroup.service;

import es.cefa.tv.core.auth.dto.GrantDto;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.site.repository.SiteRepository;
import es.cefa.tv.core.usergroup.model.AccessGrant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Convierte y valida concesiones de acceso entre entidades y DTO. */
@Component
@RequiredArgsConstructor
public class GrantMapper {

  private final PlayerGroupRepository playerGroups;
  private final SiteRepository sites;

  /** Destino de una concesión: un grupo de reproductores o una sede. */
  private record Target(UUID playerGroupId, UUID siteId) {}

  /**
   * Construye las concesiones de un sujeto validando que cada una apunte a un grupo o a una sede
   * existente y fusionando duplicados con el nivel más alto.
   *
   * @param requested concesiones pedidas
   * @param userGroupId grupo de usuarios al que se conceden
   * @return las entidades a guardar
   */
  public List<AccessGrant> toGrants(List<GrantDto> requested, UUID userGroupId) {
    Map<Target, AccessLevel> merged = new LinkedHashMap<>();
    if (requested != null) {
      for (GrantDto grant : requested) {
        if ((grant.playerGroupId() == null) == (grant.siteId() == null)) {
          throw ApiException.badRequest(
              "GRANT_TARGET_INVALID",
              "Cada concesión debe indicar un grupo de reproductores o una sede, no ambos");
        }
        merged.merge(
            new Target(grant.playerGroupId(), grant.siteId()), grant.level(), AccessLevel::max);
      }
    }
    return merged.entrySet().stream()
        .map(
            entry -> {
              Target target = entry.getKey();
              requireExists(target);
              AccessGrant grant = new AccessGrant();
              grant.setId(UUID.randomUUID());
              grant.setUserGroupId(userGroupId);
              grant.setPlayerGroupId(target.playerGroupId());
              grant.setSiteId(target.siteId());
              grant.setLevel(entry.getValue());
              return grant;
            })
        .toList();
  }

  /**
   * Convierte concesiones en DTO ordenados por destino.
   *
   * @param grants concesiones
   * @return los DTO
   */
  public List<GrantDto> toDtos(List<AccessGrant> grants) {
    return grants.stream()
        .map(grant -> new GrantDto(grant.getPlayerGroupId(), grant.getSiteId(), grant.getLevel()))
        .sorted(
            Comparator.comparing(
                grant ->
                    grant.playerGroupId() != null
                        ? "g" + grant.playerGroupId()
                        : "s" + grant.siteId()))
        .toList();
  }

  /** Comprueba que el grupo o la sede de la concesión existan. */
  private void requireExists(Target target) {
    if (target.playerGroupId() != null && !playerGroups.existsById(target.playerGroupId())) {
      throw ApiException.badRequest(
          "GROUP_NOT_FOUND", "Grupo de reproductores inexistente: " + target.playerGroupId());
    }
    if (target.siteId() != null && !sites.existsById(target.siteId())) {
      throw ApiException.badRequest("SITE_NOT_FOUND", "Sede inexistente: " + target.siteId());
    }
  }
}
