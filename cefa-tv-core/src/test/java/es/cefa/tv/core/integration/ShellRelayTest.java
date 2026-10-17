package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.core.shell.service.ShellSessionService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/** Pruebas del relé de la terminal remota entre el navegador y el reproductor. */
class ShellRelayTest extends AbstractIntegrationTest {

  @Autowired ShellSessionService shellService;

  private final StandardWebSocketClient client = new StandardWebSocketClient();

  @BeforeEach
  void authenticate() {
    login();
  }

  private WebSocketSession connectPlayer(String token, SocketCollector side) throws Exception {
    return client
        .execute(side, new WebSocketHttpHeaders(), URI.create(wsUrl("/ws/player?token=" + token)))
        .get(10, TimeUnit.SECONDS);
  }

  private WebSocketSession connectViewer(String cookie, String playerId, SocketCollector side)
      throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.COOKIE, cookie);
    return client
        .execute(side, headers, URI.create(wsUrl("/ws/shell/" + playerId)))
        .get(10, TimeUnit.SECONDS);
  }

  private void send(WebSocketSession session, Map<String, Object> message) throws Exception {
    session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
  }

  private JsonNode audits(String playerId) throws Exception {
    return adminGet("/api/v1/players/" + playerId + "/shell-audits");
  }

  private JsonNode waitForClosedAudit(String playerId) throws Exception {
    for (int i = 0; i < 50; i++) {
      JsonNode list = audits(playerId);
      if (!list.isEmpty() && !list.get(0).get("closedAt").isNull()) {
        return list.get(0);
      }
      Thread.sleep(100);
    }
    throw new AssertionError("La auditoría no se cerró");
  }

  @Test
  void relaysInputOutputResizeAndExitAndAuditsTheSession() throws Exception {
    JsonNode enrolled = enroll("pantalla-shell");
    String token = enrolled.get("deviceToken").asText();
    String playerId = enrolled.get("playerId").asText();
    SocketCollector playerSide = new SocketCollector();
    WebSocketSession player = connectPlayer(token, playerSide);
    SocketCollector viewerSide = new SocketCollector();
    WebSocketSession viewer = connectViewer(sessionCookie, playerId, viewerSide);

    send(viewer, Map.of("type", "OPEN", "cols", 100, "rows", 30));
    JsonNode open = mapper.readTree(playerSide.nextContaining("SHELL_OPEN"));
    String sessionId = open.get("sessionId").asText();
    assertThat(open.get("cols").asInt()).isEqualTo(100);
    assertThat(open.get("rows").asInt()).isEqualTo(30);
    assertThat(viewerSide.nextContaining("OPENED")).contains(sessionId);

    send(viewer, Map.of("type", "INPUT", "data", "ls -la\n"));
    JsonNode input = mapper.readTree(playerSide.nextContaining("SHELL_INPUT"));
    assertThat(input.get("sessionId").asText()).isEqualTo(sessionId);
    assertThat(input.get("data").asText()).isEqualTo("ls -la\n");

    send(viewer, Map.of("type", "RESIZE", "cols", 132, "rows", 43));
    JsonNode resize = mapper.readTree(playerSide.nextContaining("SHELL_RESIZE"));
    assertThat(resize.get("cols").asInt()).isEqualTo(132);

    send(player, Map.of("type", "SHELL_OUTPUT", "sessionId", sessionId, "data", "total 0\r\n"));
    JsonNode output = mapper.readTree(viewerSide.nextContaining("OUTPUT"));
    assertThat(output.get("data").asText()).isEqualTo("total 0\r\n");

    send(player, Map.of("type", "SHELL_EXIT", "sessionId", sessionId, "code", 0));
    assertThat(viewerSide.nextContaining("EXIT")).contains("\"code\":0");
    viewerSide.awaitClosed();

    JsonNode audit = waitForClosedAudit(playerId);
    assertThat(audit.get("username").asText()).isEqualTo(ADMIN_USER);
    assertThat(audit.get("bytesIn").asLong()).isEqualTo(7);
    assertThat(audit.get("bytesOut").asLong()).isEqualTo(9);
    assertThat(audit.get("closeReason").asText()).isEqualTo("PROCESS_EXIT");
    assertThat(audit.get("playerName").asText()).isEqualTo("pantalla-shell");
    player.close();
  }

  @Test
  void aSecondSessionOnTheSamePlayerIsRejected() throws Exception {
    JsonNode enrolled = enroll("pantalla-busy");
    String playerId = enrolled.get("playerId").asText();
    SocketCollector playerSide = new SocketCollector();
    WebSocketSession player = connectPlayer(enrolled.get("deviceToken").asText(), playerSide);
    SocketCollector firstSide = new SocketCollector();
    WebSocketSession first = connectViewer(sessionCookie, playerId, firstSide);
    send(first, Map.of("type", "OPEN"));
    playerSide.nextContaining("SHELL_OPEN");

    SocketCollector secondSide = new SocketCollector();
    WebSocketSession second = connectViewer(sessionCookie, playerId, secondSide);
    send(second, Map.of("type", "OPEN"));

    assertThat(secondSide.nextContaining("ERROR")).contains("SHELL_BUSY");
    first.close();
    playerSide.nextContaining("SHELL_CLOSE");
    second.close();
    player.close();
  }

  @Test
  void closingTheBrowserClosesTheShellOnThePlayer() throws Exception {
    JsonNode enrolled = enroll("pantalla-viewer-close");
    String playerId = enrolled.get("playerId").asText();
    SocketCollector playerSide = new SocketCollector();
    WebSocketSession player = connectPlayer(enrolled.get("deviceToken").asText(), playerSide);
    SocketCollector viewerSide = new SocketCollector();
    WebSocketSession viewer = connectViewer(sessionCookie, playerId, viewerSide);
    send(viewer, Map.of("type", "OPEN"));
    String sessionId =
        mapper.readTree(playerSide.nextContaining("SHELL_OPEN")).get("sessionId").asText();

    viewer.close();

    assertThat(playerSide.nextContaining("SHELL_CLOSE")).contains(sessionId);
    assertThat(waitForClosedAudit(playerId).get("closeReason").asText())
        .isEqualTo("VIEWER_DISCONNECTED");
    player.close();
  }

  @Test
  void aPlayerDisconnectionEndsTheSessionWithAnError() throws Exception {
    JsonNode enrolled = enroll("pantalla-drop");
    String playerId = enrolled.get("playerId").asText();
    SocketCollector playerSide = new SocketCollector();
    WebSocketSession player = connectPlayer(enrolled.get("deviceToken").asText(), playerSide);
    SocketCollector viewerSide = new SocketCollector();
    WebSocketSession viewer = connectViewer(sessionCookie, playerId, viewerSide);
    send(viewer, Map.of("type", "OPEN"));
    playerSide.nextContaining("SHELL_OPEN");
    viewerSide.nextContaining("OPENED");

    player.close();

    assertThat(viewerSide.nextContaining("ERROR")).contains("PLAYER_DISCONNECTED");
    viewerSide.awaitClosed();
    assertThat(waitForClosedAudit(playerId).get("closeReason").asText())
        .isEqualTo("PLAYER_DISCONNECTED");
  }

  @Test
  void opensFailWhenThePlayerIsOffline() throws Exception {
    JsonNode enrolled = enroll("pantalla-offline-shell");
    String playerId = enrolled.get("playerId").asText();
    SocketCollector viewerSide = new SocketCollector();
    WebSocketSession viewer = connectViewer(sessionCookie, playerId, viewerSide);

    send(viewer, Map.of("type", "OPEN"));

    assertThat(viewerSide.nextContaining("ERROR")).contains("PLAYER_OFFLINE");
    assertThat(audits(playerId)).isEmpty();
    viewer.close();
  }

  @Test
  void idleSessionsAreClosedByTheExpirationCheck() throws Exception {
    JsonNode enrolled = enroll("pantalla-idle");
    String playerId = enrolled.get("playerId").asText();
    SocketCollector playerSide = new SocketCollector();
    WebSocketSession player = connectPlayer(enrolled.get("deviceToken").asText(), playerSide);
    SocketCollector viewerSide = new SocketCollector();
    WebSocketSession viewer = connectViewer(sessionCookie, playerId, viewerSide);
    send(viewer, Map.of("type", "OPEN"));
    playerSide.nextContaining("SHELL_OPEN");

    shellService.expireSessions(Instant.now().plusSeconds(901));

    assertThat(viewerSide.nextContaining("ERROR")).contains("IDLE_TIMEOUT");
    assertThat(playerSide.nextContaining("SHELL_CLOSE")).contains("sessionId");
    assertThat(waitForClosedAudit(playerId).get("closeReason").asText()).isEqualTo("IDLE_TIMEOUT");
    player.close();
  }

  @Test
  void editorsCannotOpenTheTerminalNorReadTheAudit() throws Exception {
    String editorName = "scheduler-" + UUID.randomUUID();
    adminPost(
        "/api/v1/users",
        Map.of(
            "username",
            editorName,
            "displayName",
            "Programador",
            "password",
            "editor-pass-1234",
            "groupIds",
            List.of(schedulerGroup())));
    ResponseEntity<String> loginResponse =
        rest.postForEntity(
            url("/api/v1/auth/login"),
            Map.of("username", editorName, "password", "editor-pass-1234"),
            String.class);
    String setCookie = loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    String editorCookie = setCookie.substring(0, setCookie.indexOf(';'));
    String playerId = enroll("pantalla-editor").get("playerId").asText();

    assertThatThrownBy(() -> connectViewer(editorCookie, playerId, new SocketCollector()))
        .isInstanceOf(ExecutionException.class);

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, editorCookie);
    ResponseEntity<String> response =
        rest.exchange(
            url("/api/v1/players/" + playerId + "/shell-audits"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void anonymousClientsCannotOpenTheTerminal() throws Exception {
    String playerId = enroll("pantalla-anon").get("playerId").asText();

    assertThatThrownBy(
            () ->
                client
                    .execute(
                        new SocketCollector(),
                        new WebSocketHttpHeaders(),
                        URI.create(wsUrl("/ws/shell/" + playerId)))
                    .get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
  }
}
