package es.cefa.tv.core.player.service;

import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.player.model.IdleLogo;
import es.cefa.tv.core.player.model.IdleLogoSource;
import es.cefa.tv.core.player.model.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resuelve el logo efectivo de la pantalla sin programación: reproductor, grupo o CEFA. */
@Component
@RequiredArgsConstructor
public class IdleLogoResolver {

  /** Logo que se usa cuando ni el reproductor ni su grupo eligen uno. */
  public static final IdleLogo DEFAULT_LOGO = IdleLogo.CEFA;

  private final PlayerGroupRepository groups;

  /**
   * Logo efectivo y su origen.
   *
   * @param logo logo que debe mostrar el reproductor
   * @param source de dónde sale
   */
  public record Effective(IdleLogo logo, IdleLogoSource source) {}

  /**
   * Resuelve el logo efectivo de un reproductor consultando su grupo.
   *
   * @param player reproductor
   * @return el logo efectivo y su origen
   */
  public Effective effective(Player player) {
    IdleLogo groupLogo =
        player.getGroupId() == null
            ? null
            : groups.findById(player.getGroupId()).map(PlayerGroup::getIdleLogo).orElse(null);
    return resolve(player.getIdleLogo(), groupLogo);
  }

  /**
   * Resuelve el logo efectivo con los logos de grupo ya cargados.
   *
   * @param player reproductor
   * @param groupLogos logo propio de cada grupo que lo define, por identificador
   * @return el logo efectivo y su origen
   */
  public Effective effective(Player player, Map<UUID, IdleLogo> groupLogos) {
    IdleLogo groupLogo = player.getGroupId() == null ? null : groupLogos.get(player.getGroupId());
    return resolve(player.getIdleLogo(), groupLogo);
  }

  /**
   * Carga el logo propio de todos los grupos que lo definen.
   *
   * @return los logos de grupo por identificador
   */
  public Map<UUID, IdleLogo> groupLogos() {
    Map<UUID, IdleLogo> logos = new HashMap<>();
    for (PlayerGroup group : groups.findAll()) {
      if (group.getIdleLogo() != null) {
        logos.put(group.getId(), group.getIdleLogo());
      }
    }
    return logos;
  }

  /** Aplica la regla de herencia. */
  private static Effective resolve(IdleLogo own, IdleLogo group) {
    if (own != null) {
      return new Effective(own, IdleLogoSource.PLAYER);
    }
    if (group != null) {
      return new Effective(group, IdleLogoSource.GROUP);
    }
    return new Effective(DEFAULT_LOGO, IdleLogoSource.DEFAULT);
  }
}
