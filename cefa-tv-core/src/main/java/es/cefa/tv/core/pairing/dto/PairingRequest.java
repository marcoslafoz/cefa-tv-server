package es.cefa.tv.core.pairing.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Petición de emparejamiento enviada por un reproductor sin estado.
 *
 * @param hostname nombre del equipo
 * @param hardware datos de hardware libres
 * @param resolution resolución como texto {@code 1920x1080} o como objeto con ancho y alto
 * @param playerVersion versión del software reproductor
 * @param playerBuild commit corto con el que se construyó el reproductor
 * @param timezone zona horaria del equipo
 */
public record PairingRequest(
    String hostname,
    Map<String, Object> hardware,
    JsonNode resolution,
    String playerVersion,
    String playerBuild,
    String timezone) {}
