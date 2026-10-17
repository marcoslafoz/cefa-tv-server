package es.cefa.tv.core.pairing.service;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.group.repository.PlayerGroupRepository;
import es.cefa.tv.core.group.service.GroupSiteService;
import es.cefa.tv.core.manifest.service.ManifestService;
import es.cefa.tv.core.pairing.dto.ClaimRequest;
import es.cefa.tv.core.pairing.dto.PairingRequest;
import es.cefa.tv.core.pairing.dto.PairingRequestResponse;
import es.cefa.tv.core.pairing.dto.PairingStatusResponse;
import es.cefa.tv.core.pairing.dto.PendingPairingResponse;
import es.cefa.tv.core.pairing.model.Pairing;
import es.cefa.tv.core.pairing.model.PairingAudit;
import es.cefa.tv.core.pairing.model.PairingStatus;
import es.cefa.tv.core.pairing.repository.PairingRepository;
import es.cefa.tv.core.player.dto.PlayerResponse;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.model.PlayerStatus;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerService;
import es.cefa.tv.core.shared.config.CoreProperties;
import es.cefa.tv.core.shared.event.ContentChangedEvent;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.shared.util.Hashes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emparejamiento de reproductores: el reproductor muestra un código y un usuario lo introduce. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PairingService {

  private static final Duration CODE_TTL = Duration.ofMinutes(15);
  private static final Duration RETENTION = Duration.ofHours(24);
  private static final int POLL_INTERVAL_SEC = 3;
  private static final int MAX_PENDING_PER_ORIGIN = 10;
  private static final int TOKEN_BYTES = 32;
  private static final String INVALID_CODE_MESSAGE =
      "Código de emparejamiento no válido o caducado";

  private final PairingRepository pairings;
  private final PlayerRepository players;
  private final PlayerGroupRepository playerGroups;
  private final GroupSiteService groupSites;
  private final PlayerService playerService;
  private final ManifestService manifestService;
  private final PairingAuditService audits;
  private final ClaimAttemptLimiter limiter;
  private final AccessService access;
  private final CoreProperties properties;
  private final ApplicationEventPublisher events;
  private final SecureRandom random = new SecureRandom();

  /**
   * Crea una petición de emparejamiento y devuelve el código que debe mostrar el reproductor.
   *
   * @param request datos del reproductor
   * @param remoteAddress dirección desde la que se conecta
   * @return el código, el secreto y la caducidad
   */
  @Transactional
  public PairingRequestResponse request(PairingRequest request, String remoteAddress) {
    Instant now = Instant.now();
    supersedeSameOrigin(request.hostname(), remoteAddress);
    String secret = randomToken();
    Pairing pairing = new Pairing();
    pairing.setId(UUID.randomUUID());
    pairing.setCode(uniqueCode());
    pairing.setSecretHash(Hashes.sha256Hex(secret));
    pairing.setHostname(request.hostname());
    pairing.setIp(remoteAddress);
    pairing.setHardware(request.hardware());
    pairing.setResolution(normalizeResolution(request.resolution()));
    pairing.setPlayerVersion(request.playerVersion());
    pairing.setPlayerBuild(request.playerBuild());
    pairing.setTimezone(request.timezone());
    pairing.setStatus(PairingStatus.PENDING);
    pairing.setExpiresAt(now.plus(CODE_TTL));
    pairing.setCreatedAt(now);
    pairings.save(pairing);
    return new PairingRequestResponse(
        pairing.getId(), pairing.getCode(), secret, pairing.getExpiresAt(), POLL_INTERVAL_SEC);
  }

  /**
   * Devuelve el estado de una petición. Al estar aprobada entrega un token nuevo cada vez, de forma
   * que una respuesta perdida no deje al reproductor sin credenciales; el token anterior deja de
   * valer.
   *
   * @param pairingId identificador de la petición
   * @param secret secreto entregado al pedir el código
   * @return el estado y, si está aprobada, las credenciales
   */
  @Transactional
  public PairingStatusResponse poll(UUID pairingId, String secret) {
    Pairing pairing = pairings.findByIdForUpdate(pairingId).orElse(null);
    if (pairing == null) {
      return PairingStatusResponse.of("EXPIRED");
    }
    if (!constantTimeEquals(
        pairing.getSecretHash(), Hashes.sha256Hex(secret == null ? "" : secret))) {
      throw ApiException.unauthorized(
          "INVALID_PAIRING_SECRET", "Secreto de emparejamiento no válido");
    }
    return switch (pairing.getStatus()) {
      case PENDING -> pollPending(pairing);
      case APPROVED -> deliverCredentials(pairing);
      case EXPIRED, CONSUMED -> PairingStatusResponse.of("EXPIRED");
    };
  }

  /**
   * Lista los reproductores sin emparejar. No incluye los códigos.
   *
   * @return las peticiones vigentes
   */
  @Transactional(readOnly = true)
  public List<PendingPairingResponse> pending() {
    return pairings
        .findByStatusAndExpiresAtAfterOrderByCreatedAtDesc(PairingStatus.PENDING, Instant.now())
        .stream()
        .map(
            pairing ->
                new PendingPairingResponse(
                    pairing.getId(),
                    pairing.getHostname(),
                    pairing.getIp(),
                    pairing.getHardware(),
                    pairing.getResolution(),
                    pairing.getPlayerVersion(),
                    pairing.getPlayerBuild(),
                    pairing.getCreatedAt(),
                    pairing.getExpiresAt()))
        .toList();
  }

  /**
   * Empareja el reproductor que muestra el código indicado.
   *
   * @param request código, nombre y grupo
   * @param user usuario que empareja
   * @return el reproductor creado
   */
  @Transactional
  public PlayerResponse claim(ClaimRequest request, AuthenticatedUser user) {
    Instant now = Instant.now();
    if (limiter.isBlocked(user.id(), now)) {
      audits.record(user, PairingAudit.Outcome.RATE_LIMITED, null);
      throw ApiException.tooManyRequests(
          "TOO_MANY_ATTEMPTS", "Demasiados códigos erróneos. Espera un minuto");
    }
    requireScope(request.groupId());
    Pairing pairing = findPending(PairingCodes.normalize(request.code()), now);
    if (pairing == null) {
      limiter.recordFailure(user.id(), now);
      audits.record(user, PairingAudit.Outcome.INVALID, null);
      throw new ApiException(
          HttpStatus.NOT_FOUND, "INVALID_PAIRING_CODE", INVALID_CODE_MESSAGE, Map.of());
    }
    Player player = createPlayer(pairing, request, user, now);
    limiter.reset(user.id());
    audits.record(user, PairingAudit.Outcome.SUCCESS, player.getId());
    manifestService.rebuild(player.getId(), false);
    events.publishEvent(new ContentChangedEvent("player-paired"));
    return playerService.toResponse(player, groupNames(player.getGroupId()));
  }

  /**
   * Marca como consumido el emparejamiento de un reproductor que ya usa su token.
   *
   * @param playerId reproductor
   */
  @Transactional
  public void markConsumed(UUID playerId) {
    pairings.consumeByPlayerId(playerId);
  }

  /** Caduca las peticiones vencidas y borra las de más de 24 horas. */
  @Scheduled(fixedDelay = 60_000)
  @Transactional
  public void cleanup() {
    Instant now = Instant.now();
    int expired = pairings.expirePending(now);
    int deleted = pairings.deleteCreatedBefore(now.minus(RETENTION));
    if (expired > 0 || deleted > 0) {
      log.debug("Emparejamientos: {} caducados, {} borrados", expired, deleted);
    }
  }

  /** Comprueba que el usuario pueda dar de alta un reproductor en el grupo indicado. */
  private void requireScope(UUID groupId) {
    if (groupId == null) {
      access.requireAdmin();
    } else {
      access.requireGroupLevel(groupId, AccessLevel.OPERATE);
    }
  }

  /** Busca el emparejamiento pendiente con el código comparando todos en tiempo constante. */
  private Pairing findPending(String code, Instant now) {
    Pairing match = null;
    byte[] wanted = code.getBytes(StandardCharsets.UTF_8);
    for (Pairing candidate :
        pairings.findByStatusAndExpiresAtAfterOrderByCreatedAtDesc(PairingStatus.PENDING, now)) {
      if (MessageDigest.isEqual(candidate.getCode().getBytes(StandardCharsets.UTF_8), wanted)) {
        match = candidate;
      }
    }
    return match == null
        ? null
        : pairings
            .findByIdForUpdate(match.getId())
            .filter(locked -> locked.getStatus() == PairingStatus.PENDING)
            .orElse(null);
  }

  /** Crea el reproductor a partir del emparejamiento y lo marca como aprobado. */
  private Player createPlayer(
      Pairing pairing, ClaimRequest request, AuthenticatedUser user, Instant now) {
    Player player = new Player();
    player.setId(UUID.randomUUID());
    player.setName(uniqueName(displayName(request, pairing)));
    player.setGroupId(request.groupId());
    groupSites.inheritOnCreate(player);
    player.setStatus(PlayerStatus.ACTIVE);
    player.setDeviceTokenHash(Hashes.sha256Hex(randomToken()));
    player.setHostname(pairing.getHostname());
    player.setIp(pairing.getIp());
    player.setHardware(pairing.getHardware());
    player.setResolution(pairing.getResolution());
    player.setPlayerVersion(pairing.getPlayerVersion());
    player.setPlayerBuild(pairing.getPlayerBuild());
    player.setTimezone(pairing.getTimezone());
    player.setLastSeenAt(now);
    player.setCreatedAt(now);
    players.saveAndFlush(player);
    pairing.setStatus(PairingStatus.APPROVED);
    pairing.setPlayerId(player.getId());
    pairing.setApprovedBy(user.username());
    pairing.setApprovedAt(now);
    return player;
  }

  /** Resuelve el estado de una petición pendiente, caducándola si venció. */
  private PairingStatusResponse pollPending(Pairing pairing) {
    if (!pairing.getExpiresAt().isAfter(Instant.now())) {
      pairing.setStatus(PairingStatus.EXPIRED);
      return PairingStatusResponse.of("EXPIRED");
    }
    return PairingStatusResponse.of("PENDING");
  }

  /** Entrega al reproductor un token nuevo, invalidando el anterior. */
  private PairingStatusResponse deliverCredentials(Pairing pairing) {
    Player player =
        pairing.getPlayerId() == null ? null : players.findById(pairing.getPlayerId()).orElse(null);
    if (player == null) {
      pairing.setStatus(PairingStatus.EXPIRED);
      return PairingStatusResponse.of("EXPIRED");
    }
    String token = randomToken();
    player.setDeviceTokenHash(Hashes.sha256Hex(token));
    return new PairingStatusResponse(
        "APPROVED", player.getId(), token, player.getName(), properties.player().pollIntervalSec());
  }

  /** Caduca las peticiones pendientes del mismo origen que se sustituyen por la nueva. */
  private void supersedeSameOrigin(String hostname, String remoteAddress) {
    List<Pairing> pending =
        pairings.findByIpAndStatusOrderByCreatedAtAsc(remoteAddress, PairingStatus.PENDING);
    List<Pairing> keep = new java.util.ArrayList<>();
    for (Pairing pairing : pending) {
      if (java.util.Objects.equals(pairing.getHostname(), hostname)) {
        pairing.setStatus(PairingStatus.EXPIRED);
      } else {
        keep.add(pairing);
      }
    }
    while (keep.size() >= MAX_PENDING_PER_ORIGIN) {
      keep.remove(0).setStatus(PairingStatus.EXPIRED);
    }
    pairings.flush();
  }

  /** Genera un código que no esté en uso por otra petición pendiente. */
  private String uniqueCode() {
    String code;
    do {
      code = PairingCodes.generate();
    } while (pairings.existsByCodeAndStatus(code, PairingStatus.PENDING));
    return code;
  }

  /** Genera un token aleatorio en base64 URL. */
  private String randomToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Compara dos textos en tiempo constante. */
  private boolean constantTimeEquals(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  /** Elige el nombre pedido o, si falta, el del equipo. */
  private String displayName(ClaimRequest request, Pairing pairing) {
    if (request.name() != null && !request.name().isBlank()) {
      return request.name().trim();
    }
    return pairing.getHostname() == null || pairing.getHostname().isBlank()
        ? "Reproductor"
        : pairing.getHostname();
  }

  /** Añade un sufijo numérico al nombre si ya existe otro reproductor igual. */
  private String uniqueName(String base) {
    String candidate = base;
    int suffix = 2;
    while (players.existsByName(candidate)) {
      candidate = base + " " + suffix++;
    }
    return candidate;
  }

  /** Construye el mapa de nombres de grupo para la respuesta. */
  private Map<UUID, String> groupNames(UUID groupId) {
    if (groupId == null) {
      return Map.of();
    }
    return playerGroups
        .findById(groupId)
        .map(group -> Map.of(group.getId(), group.getName()))
        .orElse(Map.of());
  }

  /** Convierte la resolución, textual o con ancho y alto, al formato {@code 1920x1080}. */
  private String normalizeResolution(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return node.asText();
    }
    JsonNode width = node.hasNonNull("width") ? node.get("width") : node.get("w");
    JsonNode height = node.hasNonNull("height") ? node.get("height") : node.get("h");
    return width != null && height != null ? width.asInt() + "x" + height.asInt() : null;
  }
}
