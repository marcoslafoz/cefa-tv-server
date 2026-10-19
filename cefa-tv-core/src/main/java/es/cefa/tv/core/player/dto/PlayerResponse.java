package es.cefa.tv.core.player.dto;

import es.cefa.tv.core.player.model.IdleLogo;
import es.cefa.tv.core.player.model.IdleLogoSource;
import es.cefa.tv.core.player.model.PlayerKind;
import es.cefa.tv.core.player.model.PlayerStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reproductor registrado.
 *
 * @param id identificador
 * @param name nombre
 * @param groupId grupo
 * @param groupName nombre del grupo
 * @param description descripción libre del reproductor
 * @param siteId sede en la que está
 * @param siteName nombre de la sede
 * @param fallbackPlaylistId playlist de reposo propia
 * @param status estado administrativo
 * @param hostname nombre de equipo
 * @param ip dirección IP
 * @param hardware datos de hardware
 * @param resolution resolución de la salida de vídeo elegida
 * @param preferredOutput salida de vídeo que el usuario eligió para el reproductor, o {@code null}
 *     si es automática (la primera conectada)
 * @param idleLogo logo propio de la pantalla sin programación, o {@code null} si hereda
 * @param effectiveIdleLogo logo que realmente muestra: el propio, el de su grupo o el de por
 *     defecto
 * @param idleLogoSource de dónde sale el logo efectivo
 * @param system últimos datos de telemetría del equipo
 * @param displays salidas de vídeo detectadas
 * @param orientation orientación
 * @param playerVersion versión del software reproductor
 * @param playerBuild commit corto con el que se construyó el reproductor, o {@code null} si no lo
 *     informa
 * @param timezone zona horaria
 * @param online si está en línea
 * @param lastSeenAt último contacto
 * @param desiredVersion versión de programación deseada
 * @param appliedVersion versión aplicada
 * @param pending si hay versión pendiente de aplicar
 * @param state estado de reproducción
 * @param currentPlaylistId playlist en reproducción
 * @param currentAssetId medio en reproducción
 * @param lastError último error reportado
 * @param createdAt fecha de alta
 * @param neverConnected si lleva más de los días configurados dada de alta sin haberse conectado
 *     nunca, señal de un emparejamiento huérfano
 * @param previewAt instante de la última vista previa guardada, o {@code null} si no hay
 * @param currentAssetThumbnailUrl miniatura del medio que está sonando, o {@code null}
 * @param kind tipo de dispositivo
 */
public record PlayerResponse(
    UUID id,
    String name,
    UUID groupId,
    String groupName,
    String description,
    UUID siteId,
    String siteName,
    UUID fallbackPlaylistId,
    PlayerStatus status,
    String hostname,
    String ip,
    Map<String, Object> hardware,
    String resolution,
    String preferredOutput,
    IdleLogo idleLogo,
    IdleLogo effectiveIdleLogo,
    IdleLogoSource idleLogoSource,
    Map<String, Object> system,
    List<Map<String, Object>> displays,
    String orientation,
    String playerVersion,
    String playerBuild,
    String timezone,
    boolean online,
    Instant lastSeenAt,
    long desiredVersion,
    long appliedVersion,
    boolean pending,
    String state,
    UUID currentPlaylistId,
    UUID currentAssetId,
    String lastError,
    Instant createdAt,
    boolean neverConnected,
    Instant previewAt,
    String currentAssetThumbnailUrl,
    PlayerKind kind) {}
