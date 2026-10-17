package es.cefa.tv.core.playerapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.core.manifest.model.PlayerManifest;
import es.cefa.tv.core.manifest.service.ManifestService;
import es.cefa.tv.core.media.model.StoredFile;
import es.cefa.tv.core.playerapi.dto.AckRequest;
import es.cefa.tv.core.playerapi.dto.FilesRequest;
import es.cefa.tv.core.playerapi.dto.HeartbeatRequest;
import es.cefa.tv.core.playerapi.dto.HeartbeatResponse;
import es.cefa.tv.core.playerapi.security.AuthenticatedPlayer;
import es.cefa.tv.core.playerapi.service.FileDeliveryService;
import es.cefa.tv.core.playerapi.service.PlayerApiService;
import es.cefa.tv.core.shared.storage.ObjectStorageService;
import es.cefa.tv.core.shared.storage.RangeResponses;
import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** API consumida por los reproductores. */
@RestController
@RequestMapping("/player-api")
@RequiredArgsConstructor
public class PlayerApiController {

  private final PlayerApiService playerApiService;
  private final ManifestService manifestService;
  private final FileDeliveryService fileDeliveryService;
  private final ObjectStorageService storage;
  private final ObjectMapper objectMapper;

  /**
   * Devuelve el manifest vigente, comprimido con gzip si el cliente lo acepta.
   *
   * @param player reproductor autenticado
   * @param ifNoneMatch ETag que ya tiene el cliente
   * @param acceptEncoding codificaciones aceptadas
   * @return el manifest o 304 si no ha cambiado
   * @throws IOException si no se puede serializar
   */
  @GetMapping("/manifest")
  public ResponseEntity<byte[]> manifest(
      @AuthenticationPrincipal AuthenticatedPlayer player,
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
      @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) String acceptEncoding)
      throws IOException {
    PlayerManifest manifest = manifestService.latest(player.playerId());
    String etag = "\"" + manifest.getVersion() + "\"";
    if (etag.equals(ifNoneMatch)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
    }
    byte[] json = objectMapper.writeValueAsBytes(manifest.getManifest());
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).eTag(etag);
    if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
        gzip.write(json);
      }
      return builder.header(HttpHeaders.CONTENT_ENCODING, "gzip").body(buffer.toByteArray());
    }
    return builder.body(json);
  }

  /**
   * Devuelve un zip con los ficheros pedidos.
   *
   * @param request resúmenes pedidos
   * @return el zip en streaming
   */
  @PostMapping("/files")
  public ResponseEntity<StreamingResponseBody> files(@Valid @RequestBody FilesRequest request) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(fileDeliveryService.zip(request.sha256()));
  }

  /**
   * Descarga un fichero con soporte de rangos.
   *
   * @param sha256 resumen del fichero
   * @param range cabecera {@code Range}
   * @return el fichero completo o el tramo pedido
   */
  @GetMapping("/files/{sha256}")
  public ResponseEntity<StreamingResponseBody> file(
      @PathVariable String sha256,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
    StoredFile file = fileDeliveryService.find(sha256);
    return RangeResponses.serve(
        storage,
        file.getObjectKey(),
        file.getSizeBytes(),
        file.getMime(),
        file.getSha256(),
        range,
        file.getName());
  }

  /**
   * Recibe el estado periódico del reproductor.
   *
   * @param player reproductor autenticado
   * @param request estado comunicado
   * @return la versión deseada y las órdenes pendientes
   */
  @PostMapping("/heartbeat")
  public HeartbeatResponse heartbeat(
      @AuthenticationPrincipal AuthenticatedPlayer player, @RequestBody HeartbeatRequest request) {
    return playerApiService.heartbeat(player.playerId(), request);
  }

  /**
   * Recibe la confirmación de una versión aplicada o fallida.
   *
   * @param player reproductor autenticado
   * @param request resultado
   */
  @PostMapping("/ack")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void ack(
      @AuthenticationPrincipal AuthenticatedPlayer player, @Valid @RequestBody AckRequest request) {
    playerApiService.acknowledge(player.playerId(), request);
  }
}
