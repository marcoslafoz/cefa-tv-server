package es.cefa.tv.core.preview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.core.auth.security.AuthenticatedUser;
import es.cefa.tv.core.authorization.model.AccessLevel;
import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessPolicy;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.realtime.PlayerSessionRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Vista previa en vivo de las pantallas, pensada para gastar lo mínimo: el reproductor solo captura
 * mientras alguien la mira. El servidor cuenta los suscriptores de cada reproductor, le ordena
 * empezar con el primero y parar con el último, y retransmite cada fotograma reducido a quienes lo
 * observan. De cada reproductor solo se guarda en memoria el último fotograma.
 */
@Slf4j
@Service
public class PreviewService {

  /** Número máximo de reproductores por suscriptor. */
  public static final int MAX_PLAYERS_PER_SUBSCRIBER = 48;

  /** Intervalo mínimo entre fotogramas, en segundos. */
  public static final int MIN_INTERVAL_SEC = 5;

  /** Intervalo máximo entre fotogramas, en segundos. */
  public static final int MAX_INTERVAL_SEC = 60;

  /** Intervalo por defecto entre fotogramas, en segundos. */
  public static final int DEFAULT_INTERVAL_SEC = 10;

  /** Tamaño máximo de un fotograma JPEG; los mayores se descartan. */
  public static final int MAX_FRAME_BYTES = 25 * 1024;

  /** Número máximo de fotogramas guardados en memoria. */
  public static final int MAX_CACHED_FRAMES = 512;

  /** Memoria máxima de los fotogramas guardados. */
  public static final int MAX_CACHED_BYTES = 16 * 1024 * 1024;

  private static final Duration SUBSCRIPTION_TTL = Duration.ofSeconds(30);
  private static final int PREVIEW_WIDTH = 320;
  private static final int PREVIEW_QUALITY = 45;
  private static final int UUID_TEXT_BYTES = 36;
  private static final String STOP_MESSAGE = "{\"type\":\"PREVIEW_STOP\"}";

  private final PlayerSessionRegistry registry;
  private final PlayerRepository players;
  private final ObjectMapper objectMapper;
  private final Counter acceptedFrames;
  private final Counter droppedFrames;
  private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> requestedIntervals = new ConcurrentHashMap<>();
  private final Map<UUID, PreviewFrame> frames = new LinkedHashMap<>(16, 0.75f, true);
  private final AtomicLong framesThisMinute = new AtomicLong();
  private final AtomicLong droppedThisMinute = new AtomicLong();
  private long cachedBytes;

  /** Suscriptor: una conexión de navegador con los reproductores que observa. */
  private static final class Subscriber {
    final WebSocketSession session;
    final AuthenticatedUser user;
    final Set<UUID> playerIds = new LinkedHashSet<>();
    int intervalSec = DEFAULT_INTERVAL_SEC;
    volatile Instant renewedAt = Instant.now();

    Subscriber(WebSocketSession session, AuthenticatedUser user) {
      this.session = session;
      this.user = user;
    }
  }

  /**
   * Crea el servicio.
   *
   * @param registry registro de conexiones de reproductores
   * @param players reproductores
   * @param objectMapper serializador JSON
   * @param meters registro de métricas
   */
  public PreviewService(
      PlayerSessionRegistry registry,
      PlayerRepository players,
      ObjectMapper objectMapper,
      MeterRegistry meters) {
    this.registry = registry;
    this.players = players;
    this.objectMapper = objectMapper;
    this.acceptedFrames = meters.counter("cefatv.preview.frames", "result", "relayed");
    this.droppedFrames = meters.counter("cefatv.preview.frames", "result", "dropped");
    meters.gauge("cefatv.preview.subscribers", subscribers, Map::size);
  }

  /**
   * Suscribe una conexión a la vista previa de unos reproductores y renueva su suscripción. Solo se
   * aceptan los reproductores que el usuario puede ver; los demás se ignoran.
   *
   * @param session conexión del navegador, ya preparada para envíos concurrentes
   * @param user usuario de la conexión
   * @param requested reproductores pedidos
   * @param intervalSec segundos entre fotogramas, o {@code null} para el valor por defecto
   * @return los reproductores a los que queda suscrita la conexión
   */
  public synchronized List<UUID> subscribe(
      WebSocketSession session,
      AuthenticatedUser user,
      Collection<UUID> requested,
      Integer intervalSec) {
    Subscriber subscriber =
        subscribers.computeIfAbsent(session.getId(), id -> new Subscriber(session, user));
    subscriber.renewedAt = Instant.now();
    subscriber.intervalSec = clampInterval(intervalSec);
    Set<UUID> changed = new HashSet<>(subscriber.playerIds);
    for (UUID playerId : requested) {
      if (subscriber.playerIds.size() >= MAX_PLAYERS_PER_SUBSCRIBER) {
        break;
      }
      if (canView(user, playerId)) {
        subscriber.playerIds.add(playerId);
        changed.add(playerId);
      }
    }
    changed.forEach(this::reconcile);
    return List.copyOf(subscriber.playerIds);
  }

  /**
   * Da de baja a una conexión de la vista previa de unos reproductores.
   *
   * @param sessionId identificador de la conexión
   * @param playerIds reproductores que deja de observar
   */
  public synchronized void unsubscribe(String sessionId, Collection<UUID> playerIds) {
    Subscriber subscriber = subscribers.get(sessionId);
    if (subscriber == null) {
      return;
    }
    subscriber.playerIds.removeAll(playerIds);
    playerIds.forEach(this::reconcile);
  }

  /**
   * Da de baja a una conexión de todo, al cerrarse.
   *
   * @param sessionId identificador de la conexión
   */
  public synchronized void close(String sessionId) {
    Subscriber subscriber = subscribers.remove(sessionId);
    if (subscriber != null) {
      new HashSet<>(subscriber.playerIds).forEach(this::reconcile);
    }
  }

  /** Da de baja a las conexiones que llevan más de 30 segundos sin renovar su suscripción. */
  @Scheduled(fixedDelay = 5_000)
  public void expireStale() {
    expireStale(Instant.now());
  }

  /**
   * Da de baja a las conexiones cuya última renovación es anterior a 30 segundos antes del instante
   * dado.
   *
   * @param now instante de referencia
   */
  public synchronized void expireStale(Instant now) {
    Instant limit = now.minus(SUBSCRIPTION_TTL);
    for (Subscriber subscriber : new ArrayList<>(subscribers.values())) {
      if (subscriber.renewedAt.isBefore(limit)) {
        subscribers.remove(subscriber.session.getId());
        send(subscriber, new TextMessage("{\"type\":\"EXPIRED\"}"));
        new HashSet<>(subscriber.playerIds).forEach(this::reconcile);
      }
    }
  }

  /**
   * Procesa un fotograma enviado por un reproductor: lo descarta si no hay quien lo mire o es
   * demasiado grande y, si no, lo guarda y lo retransmite a los suscriptores.
   *
   * @param playerId reproductor
   * @param jpeg bytes de la imagen JPEG
   */
  public synchronized void onFrame(UUID playerId, byte[] jpeg) {
    if (jpeg == null
        || jpeg.length == 0
        || jpeg.length > MAX_FRAME_BYTES
        || !requestedIntervals.containsKey(playerId)) {
      droppedFrames.increment();
      droppedThisMinute.incrementAndGet();
      return;
    }
    remember(playerId, new PreviewFrame(jpeg, Instant.now()));
    BinaryMessage message = new BinaryMessage(withPlayerId(playerId, jpeg));
    for (Subscriber subscriber : new ArrayList<>(subscribers.values())) {
      if (subscriber.playerIds.contains(playerId) && send(subscriber, message)) {
        acceptedFrames.increment();
        framesThisMinute.incrementAndGet();
      }
    }
  }

  /**
   * Reenvía la orden de empezar a un reproductor que se acaba de conectar y tiene observadores.
   *
   * @param playerId reproductor
   */
  public synchronized void onPlayerHello(UUID playerId) {
    Integer interval = requestedIntervals.get(playerId);
    if (interval != null) {
      registry.sendToPlayer(playerId, startMessage(interval));
    }
  }

  /**
   * Devuelve el último fotograma de un reproductor.
   *
   * @param playerId reproductor
   * @return el fotograma, o {@code null} si no hay
   */
  public synchronized PreviewFrame latest(UUID playerId) {
    return frames.get(playerId);
  }

  /**
   * Devuelve el instante del último fotograma de un reproductor.
   *
   * @param playerId reproductor
   * @return el instante, o {@code null} si no hay
   */
  public synchronized Instant previewAt(UUID playerId) {
    PreviewFrame frame = frames.get(playerId);
    return frame == null ? null : frame.at();
  }

  /**
   * Indica cuántos suscriptores observan a un reproductor.
   *
   * @param playerId reproductor
   * @return el número de suscriptores
   */
  public synchronized long subscriberCount(UUID playerId) {
    return subscribers.values().stream().filter(s -> s.playerIds.contains(playerId)).count();
  }

  /** Registra cada minuto cuántos fotogramas se han retransmitido y descartado. */
  @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
  public void logRates() {
    long relayed = framesThisMinute.getAndSet(0);
    long dropped = droppedThisMinute.getAndSet(0);
    if (relayed > 0 || dropped > 0) {
      log.info(
          "Vista previa: {} fotogramas/min retransmitidos, {} descartados, {} suscriptores",
          relayed,
          dropped,
          subscribers.size());
    }
  }

  /** Ajusta las órdenes al reproductor al número de observadores y al menor intervalo pedido. */
  private void reconcile(UUID playerId) {
    Integer wanted =
        subscribers.values().stream()
            .filter(subscriber -> subscriber.playerIds.contains(playerId))
            .map(subscriber -> subscriber.intervalSec)
            .min(Integer::compare)
            .orElse(null);
    Integer current = requestedIntervals.get(playerId);
    if (wanted == null && current != null) {
      requestedIntervals.remove(playerId);
      registry.sendToPlayer(playerId, STOP_MESSAGE);
    } else if (wanted != null && !wanted.equals(current)) {
      requestedIntervals.put(playerId, wanted);
      registry.sendToPlayer(playerId, startMessage(wanted));
    }
  }

  /** Construye la orden de empezar a capturar. */
  private String startMessage(int intervalSec) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("type", "PREVIEW_START");
    node.put("intervalSec", intervalSec);
    node.put("maxWidth", PREVIEW_WIDTH);
    node.put("quality", PREVIEW_QUALITY);
    return node.toString();
  }

  /** Antepone al JPEG el identificador del reproductor en 36 bytes ASCII. */
  private static ByteBuffer withPlayerId(UUID playerId, byte[] jpeg) {
    byte[] id = playerId.toString().getBytes(StandardCharsets.US_ASCII);
    ByteBuffer buffer = ByteBuffer.allocate(UUID_TEXT_BYTES + jpeg.length);
    buffer.put(id).put(jpeg).flip();
    return buffer;
  }

  /** Guarda el último fotograma de un reproductor respetando los topes de memoria. */
  private void remember(UUID playerId, PreviewFrame frame) {
    PreviewFrame previous = frames.put(playerId, frame);
    cachedBytes += frame.jpeg().length - (previous == null ? 0 : previous.jpeg().length);
    var iterator = frames.entrySet().iterator();
    while ((frames.size() > MAX_CACHED_FRAMES || cachedBytes > MAX_CACHED_BYTES)
        && iterator.hasNext()) {
      var oldest = iterator.next();
      if (!oldest.getKey().equals(playerId)) {
        cachedBytes -= oldest.getValue().jpeg().length;
        iterator.remove();
      }
    }
  }

  /** Envía un mensaje a un suscriptor y, si no puede recibirlo, lo da de baja. */
  private boolean send(
      Subscriber subscriber, org.springframework.web.socket.WebSocketMessage<?> m) {
    try {
      subscriber.session.sendMessage(m);
      return true;
    } catch (IOException | IllegalStateException e) {
      log.debug("Suscriptor de vista previa descartado: {}", e.getMessage());
      subscribers.remove(subscriber.session.getId());
      new HashSet<>(subscriber.playerIds).forEach(this::reconcile);
      return false;
    }
  }

  /** Indica si el usuario puede ver el reproductor. */
  private boolean canView(AuthenticatedUser user, UUID playerId) {
    Player player = players.findById(playerId).orElse(null);
    return player != null
        && AccessPolicy.canActOnPlayer(
            user,
            Permission.PLAYER_VIEW,
            player.getGroupId(),
            player.getSiteId(),
            AccessLevel.VIEW);
  }

  /** Limita el intervalo pedido al rango permitido. */
  private static int clampInterval(Integer requested) {
    if (requested == null) {
      return DEFAULT_INTERVAL_SEC;
    }
    return Math.max(MIN_INTERVAL_SEC, Math.min(MAX_INTERVAL_SEC, requested));
  }
}
