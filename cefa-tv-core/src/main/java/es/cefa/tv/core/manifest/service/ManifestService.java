package es.cefa.tv.core.manifest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.delivery.service.DeliveryService;
import es.cefa.tv.core.group.model.PlayerGroup;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.manifest.model.ManifestDocument;
import es.cefa.tv.core.manifest.model.PlayerManifest;
import es.cefa.tv.core.manifest.repository.PlayerManifestRepository;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerStatus;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerTimezoneResolver;
import es.cefa.tv.core.playlist.model.Playlist;
import es.cefa.tv.core.playlist.repository.PlaylistRepository;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import es.cefa.tv.core.schedule.model.Schedule;
import es.cefa.tv.core.schedule.repository.ScheduleRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shared.util.Hashes;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Calcula y versiona el manifest de cada reproductor y avisa de las versiones nuevas. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManifestService {

  private static final UUID NO_GROUP = new UUID(0L, 0L);
  private static final String SYNC_MESSAGE = "{\"type\":\"SYNC\"}";

  private final PlayerRepository players;
  private final PlayerGroupRepository groups;
  private final PlayerTimezoneResolver timezones;
  private final ScheduleRepository schedules;
  private final PlaylistRepository playlists;
  private final PlayerManifestRepository manifests;
  private final DeliveryService deliveries;
  private final PlayerSessionRegistry sessions;
  private final ObjectMapper objectMapper;

  /**
   * Recalcula el manifest de un reproductor y, si su contenido cambia o se fuerza, crea una versión
   * nueva.
   *
   * @param playerId reproductor
   * @param force si es {@code true} crea una versión nueva aunque el contenido no cambie
   * @return la versión creada, o vacío si no hubo cambios
   */
  @Transactional
  public Optional<Long> rebuild(UUID playerId, boolean force) {
    Player player =
        players.findByIdForUpdate(playerId).orElseThrow(() -> ApiException.notFound("Reproductor"));
    if (player.getStatus() != PlayerStatus.ACTIVE) {
      return Optional.empty();
    }
    UUID groupId = player.getGroupId() == null ? NO_GROUP : player.getGroupId();
    UUID siteId = player.getSiteId() == null ? NO_GROUP : player.getSiteId();
    List<Schedule> applicable = schedules.findEnabledForPlayer(playerId, groupId, siteId);
    UUID fallbackId = resolveFallback(player);

    Map<UUID, Playlist> usedPlaylists = new LinkedHashMap<>();
    for (Schedule schedule : applicable) {
      playlists.findById(schedule.getPlaylistId()).ifPresent(p -> usedPlaylists.put(p.getId(), p));
    }
    if (fallbackId != null) {
      Optional<Playlist> fallback = playlists.findById(fallbackId);
      fallback.ifPresent(p -> usedPlaylists.put(p.getId(), p));
      if (fallback.isEmpty()) {
        fallbackId = null;
      }
    }

    String timezone = timezones.timezoneOf(player);
    ManifestDocument content =
        ManifestAssembler.assemble(
            playerId, timezone, fallbackId, applicable, usedPlaylists.values());
    String hash = hashOf(content);

    Optional<PlayerManifest> latest = manifests.findFirstByPlayerIdOrderByVersionDesc(playerId);
    if (!force && latest.isPresent() && latest.get().getContentHash().equals(hash)) {
      return Optional.empty();
    }
    long version = latest.map(m -> m.getVersion() + 1).orElse(1L);
    Instant now = Instant.now();

    PlayerManifest manifest = new PlayerManifest();
    manifest.setPlayerId(playerId);
    manifest.setVersion(version);
    manifest.setContentHash(hash);
    manifest.setManifest(
        new ManifestDocument(
            version,
            now,
            content.playerId(),
            content.timezone(),
            content.fallbackPlaylistId(),
            content.files(),
            content.assets(),
            content.playlists(),
            content.schedules()));
    manifest.setCreatedAt(now);
    manifests.save(manifest);

    player.setDesiredVersion(version);
    deliveries.queue(playerId, version);
    notifyAfterCommit(playerId);
    log.debug("Manifest v{} calculado para el reproductor {}", version, playerId);
    return Optional.of(version);
  }

  /**
   * Devuelve la última versión de un reproductor.
   *
   * @param playerId reproductor
   * @return el manifest más reciente
   */
  @Transactional(readOnly = true)
  public PlayerManifest latest(UUID playerId) {
    return manifests
        .findFirstByPlayerIdOrderByVersionDesc(playerId)
        .orElseThrow(() -> ApiException.notFound("Manifest"));
  }

  /**
   * Lista los identificadores de los reproductores activos.
   *
   * @return los identificadores
   */
  @Transactional(readOnly = true)
  public List<UUID> activePlayerIds() {
    return players.findByStatus(PlayerStatus.ACTIVE).stream().map(Player::getId).toList();
  }

  /** Devuelve la playlist de reposo del reproductor o, si no tiene, la de su grupo. */
  private UUID resolveFallback(Player player) {
    if (player.getFallbackPlaylistId() != null) {
      return player.getFallbackPlaylistId();
    }
    if (player.getGroupId() == null) {
      return null;
    }
    return groups
        .findById(player.getGroupId())
        .map(PlayerGroup::getFallbackPlaylistId)
        .orElse(null);
  }

  /** Calcula el resumen del contenido del manifest sin versión ni fecha. */
  private String hashOf(ManifestDocument content) {
    try {
      return Hashes.sha256Hex(objectMapper.writeValueAsString(content));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo serializar el manifest", e);
    }
  }

  /** Avisa por WebSocket de la versión nueva cuando la transacción se confirma. */
  private void notifyAfterCommit(UUID playerId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      sessions.sendToPlayer(playerId, SYNC_MESSAGE);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            sessions.sendToPlayer(playerId, SYNC_MESSAGE);
          }
        });
  }
}
