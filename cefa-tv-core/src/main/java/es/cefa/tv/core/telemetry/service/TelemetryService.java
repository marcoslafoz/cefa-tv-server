package es.cefa.tv.core.telemetry.service;

import es.cefa.tv.core.player.model.CommandType;
import es.cefa.tv.core.player.model.Player;
import es.cefa.tv.core.player.repository.PlayerRepository;
import es.cefa.tv.core.player.service.PlayerCommandService;
import es.cefa.tv.core.shared.exception.ApiException;
import es.cefa.tv.core.telemetry.dto.MetricPoint;
import es.cefa.tv.core.telemetry.dto.ResolutionRequest;
import es.cefa.tv.core.telemetry.model.PlayerMetric;
import es.cefa.tv.core.telemetry.repository.PlayerMetricRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Almacena la telemetría de los reproductores y gestiona las órdenes de pantalla. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

  private static final Duration MIN_SAMPLE_SPACING = Duration.ofSeconds(10);
  private static final Duration RETENTION = Duration.ofHours(24);
  private static final int MIN_HOURS = 1;
  private static final int MAX_HOURS = 24;
  private static final int MAX_VERSION_LENGTH = 50;
  private static final int MAX_BUILD_LENGTH = 40;

  private final PlayerRepository players;
  private final PlayerMetricRepository metrics;
  private final PlayerCommandService commandService;
  private final Map<UUID, Instant> lastSamples = new ConcurrentHashMap<>();

  /**
   * Guarda los datos de telemetría recibidos en el estado del reproductor y añade una muestra a su
   * serie. Debe invocarse dentro de la transacción que ya gestiona al reproductor.
   *
   * @param player reproductor gestionado
   * @param system datos del equipo, o {@code null} si no se enviaron
   * @param displays salidas de vídeo, o {@code null} si no se enviaron
   */
  public void record(
      Player player, Map<String, Object> system, List<Map<String, Object>> displays) {
    if (displays != null) {
      player.setDisplays(displays);
      DisplayResolutions.selectedResolution(displays).ifPresent(player::setResolution);
    }
    if (system != null) {
      player.setSystemInfo(system);
      recordVersion(player, system);
      saveSample(player.getId(), system);
    }
  }

  /** Actualiza la versión y el commit del reproductor con lo que informa la telemetría. */
  private void recordVersion(Player player, Map<String, Object> system) {
    textOf(system, "playerVersion", MAX_VERSION_LENGTH).ifPresent(player::setPlayerVersion);
    textOf(system, "playerBuild", MAX_BUILD_LENGTH).ifPresent(player::setPlayerBuild);
  }

  /** Lee un texto no vacío de la telemetría, descartando los que no caben en su columna. */
  private static Optional<String> textOf(Map<String, Object> system, String key, int maxLength) {
    return Optional.ofNullable(system.get(key))
        .filter(String.class::isInstance)
        .map(value -> ((String) value).trim())
        .filter(value -> !value.isEmpty() && value.length() <= maxLength);
  }

  /**
   * Devuelve la serie de muestras de un reproductor.
   *
   * @param playerId reproductor
   * @param hours horas hacia atrás, acotadas entre 1 y 24
   * @return las muestras en orden cronológico
   */
  @Transactional(readOnly = true)
  public List<MetricPoint> series(UUID playerId, int hours) {
    requirePlayer(playerId);
    int clamped = Math.max(MIN_HOURS, Math.min(MAX_HOURS, hours));
    Instant since = Instant.now().minus(Duration.ofHours(clamped));
    return metrics.findByPlayerIdAndAtAfterOrderByAtAsc(playerId, since).stream()
        .map(
            m ->
                new MetricPoint(
                    m.getAt(),
                    m.getCpuPercent(),
                    m.getRamUsedMb(),
                    m.getDiskFreeMb(),
                    m.getTempC()))
        .toList();
  }

  /**
   * Pide al reproductor que vuelva a detectar sus salidas de vídeo.
   *
   * @param playerId reproductor
   */
  @Transactional
  public void redetectDisplays(UUID playerId) {
    requirePlayer(playerId);
    commandService.send(playerId, CommandType.DISPLAY_REDETECT);
  }

  /**
   * Pide al reproductor que cambie la resolución de una salida.
   *
   * @param playerId reproductor
   * @param request salida y resolución pedidas
   */
  @Transactional
  public void setResolution(UUID playerId, ResolutionRequest request) {
    Player player = requirePlayer(playerId);
    List<Map<String, Object>> known = player.getDisplays();
    if (known != null
        && !known.isEmpty()
        && !DisplayResolutions.isConnected(known, request.output())) {
      throw ApiException.badRequest("DISPLAY_NOT_FOUND", "La salida no existe o no está conectada");
    }
    commandService.send(
        playerId,
        CommandType.SET_RESOLUTION,
        Map.of("output", request.output(), "width", request.width(), "height", request.height()));
  }

  /** Borra las muestras con más de 24 horas. */
  @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
  @Transactional
  public void purgeOldSamples() {
    int deleted = metrics.deleteOlderThan(Instant.now().minus(RETENTION));
    if (deleted > 0) {
      log.debug("Purgadas {} muestras de telemetría", deleted);
    }
  }

  /** Inserta una muestra salvo que la anterior sea demasiado reciente. */
  private void saveSample(UUID playerId, Map<String, Object> system) {
    Instant now = Instant.now();
    Instant previous = lastSamples.get(playerId);
    if (previous != null && Duration.between(previous, now).compareTo(MIN_SAMPLE_SPACING) < 0) {
      return;
    }
    lastSamples.put(playerId, now);
    PlayerMetric sample = new PlayerMetric();
    sample.setPlayerId(playerId);
    sample.setAt(now);
    sample.setCpuPercent(asDouble(system.get("cpuPercent")));
    sample.setRamUsedMb(asLong(system.get("ramUsedMb")));
    sample.setDiskFreeMb(asLong(system.get("diskFreeMb")));
    sample.setTempC(asDouble(system.get("tempC")));
    metrics.save(sample);
  }

  /** Busca el reproductor o lanza un 404. */
  private Player requirePlayer(UUID playerId) {
    return players.findById(playerId).orElseThrow(() -> ApiException.notFound("Reproductor"));
  }

  /** Convierte un valor numérico en {@code Double} o {@code null}. */
  private static Double asDouble(Object value) {
    return value instanceof Number number ? number.doubleValue() : null;
  }

  /** Convierte un valor numérico en {@code Long} o {@code null}. */
  private static Long asLong(Object value) {
    return value instanceof Number number ? number.longValue() : null;
  }
}
