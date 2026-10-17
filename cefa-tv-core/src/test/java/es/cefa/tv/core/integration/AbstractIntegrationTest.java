package es.cefa.tv.core.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas de integración: PostgreSQL y MinIO reales en contenedores y la aplicación en
 * un puerto aleatorio.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

  static final String ADMIN_USER = "admin";
  static final String ADMIN_PASSWORD = "admin-test-1234";

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static final GenericContainer<?> MINIO =
      new GenericContainer<>(
              DockerImageName.parse(
                  System.getProperty("test.minio.image", "cgr.dev/chainguard/minio:latest")))
          .withEnv("MINIO_ROOT_USER", "cefatv")
          .withEnv("MINIO_ROOT_PASSWORD", "cefatv-secret")
          .withCommand("server", "/data")
          .withExposedPorts(9000)
          .waitingFor(
              Wait.forHttp("/minio/health/live")
                  .forPort(9000)
                  .withStartupTimeout(Duration.ofSeconds(60)));

  static {
    POSTGRES.start();
    MINIO.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "cefa.tv.minio.endpoint",
        () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    registry.add("cefa.tv.minio.access-key", () -> "cefatv");
    registry.add("cefa.tv.minio.secret-key", () -> "cefatv-secret");
    registry.add("cefa.tv.admin.username", () -> ADMIN_USER);
    registry.add("cefa.tv.admin.password", () -> ADMIN_PASSWORD);
    registry.add("cefa.tv.jwt.secret", () -> "0123456789012345678901234567890123456789");
    registry.add("cefa.tv.manifest.debounce-ms", () -> "200");
  }

  @LocalServerPort int port;

  @Autowired TestRestTemplate rest;

  @Autowired ObjectMapper mapper;

  String sessionCookie;

  String url(String path) {
    return "http://localhost:" + port + path;
  }

  String wsUrl(String path) {
    return "ws://localhost:" + port + path;
  }

  void login() {
    ResponseEntity<String> response =
        rest.postForEntity(
            url("/api/v1/auth/login"),
            Map.of("username", ADMIN_USER, "password", ADMIN_PASSWORD),
            String.class);
    String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    sessionCookie = setCookie.substring(0, setCookie.indexOf(';'));
  }

  HttpHeaders adminHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, sessionCookie);
    return headers;
  }

  HttpHeaders playerHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  JsonNode adminPost(String path, Object body) throws IOException {
    HttpHeaders headers = adminHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> response =
        rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    return mapper.readTree(response.getBody() == null ? "null" : response.getBody());
  }

  JsonNode adminGet(String path) throws IOException {
    ResponseEntity<String> response =
        rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);
    return mapper.readTree(response.getBody());
  }

  JsonNode playerPost(String token, String path, Object body) throws IOException {
    HttpHeaders headers = playerHeaders(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> response =
        rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    return mapper.readTree(response.getBody() == null ? "null" : response.getBody());
  }

  JsonNode fetchManifest(String token) throws IOException {
    HttpHeaders headers = playerHeaders(token);
    headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
    ResponseEntity<byte[]> response =
        rest.exchange(
            url("/player-api/manifest"), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(response.getBody()))) {
      return mapper.readTree(gzip.readAllBytes());
    }
  }

  JsonNode enroll(String hostname) throws IOException {
    return enroll(hostname, null);
  }

  JsonNode enroll(String hostname, String groupId) throws IOException {
    HttpHeaders json = new HttpHeaders();
    json.setContentType(MediaType.APPLICATION_JSON);
    JsonNode requested =
        mapper.readTree(
            rest.exchange(
                    url("/player-api/pairing/request"),
                    HttpMethod.POST,
                    new HttpEntity<>(
                        Map.of("hostname", hostname, "timezone", "Europe/Madrid"), json),
                    String.class)
                .getBody());
    Map<String, Object> claim = new java.util.HashMap<>();
    claim.put("code", requested.get("code").asText());
    claim.put("name", hostname);
    claim.put("groupId", groupId);
    adminPost("/api/v1/pairings/claim", claim);
    HttpHeaders poll = new HttpHeaders();
    poll.add("X-Pairing-Secret", requested.get("secret").asText());
    return mapper.readTree(
        rest.exchange(
                url("/player-api/pairing/" + requested.get("pairingId").asText()),
                HttpMethod.GET,
                new HttpEntity<>(poll),
                String.class)
            .getBody());
  }

  JsonNode uploadImage(String name) throws IOException {
    return uploadImage(name, null);
  }

  JsonNode uploadImage(String name, String folderId) throws IOException {
    BufferedImage image = new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "files",
        new ByteArrayResource(bytes.toByteArray()) {
          @Override
          public String getFilename() {
            return name;
          }
        });
    if (folderId != null) {
      body.add("folderId", folderId);
    }
    HttpHeaders headers = adminHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    ResponseEntity<String> response =
        rest.exchange(
            url("/api/v1/media"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    return mapper.readTree(response.getBody()).get(0);
  }

  String loginAs(String username, String password) {
    ResponseEntity<String> response =
        rest.postForEntity(
            url("/api/v1/auth/login"),
            Map.of("username", username, "password", password),
            String.class);
    String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    return setCookie == null ? null : setCookie.substring(0, setCookie.indexOf(';'));
  }

  ResponseEntity<String> call(String cookie, HttpMethod method, String path, Object body) {
    HttpHeaders headers = new HttpHeaders();
    if (cookie != null) {
      headers.add(HttpHeaders.COOKIE, cookie);
    }
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
  }

  String errorCode(ResponseEntity<String> response) throws IOException {
    return mapper.readTree(response.getBody()).get("errorCode").asText();
  }

  static final String USER_PASSWORD = "password-1234";

  static final List<String> SCHEDULER_PERMISSIONS =
      List.of("MEDIA_MANAGE", "PLAYLIST_MANAGE", "SCHEDULE_MANAGE", "PLAYER_VIEW", "PLAYER_REMOTE");

  static final List<String> VIEWER_PERMISSIONS = List.of("PLAYER_VIEW");

  String createUser(String username, List<String> groupIds) throws IOException {
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("username", username);
    body.put("displayName", username);
    body.put("password", USER_PASSWORD);
    body.put("groupIds", groupIds);
    return adminPost("/api/v1/users", body).get("id").asText();
  }

  String createGroup(String name, List<String> permissions, List<? extends Map<String, ?>> grants)
      throws IOException {
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("name", name);
    body.put("permissions", permissions);
    body.put("grants", grants);
    return adminPost("/api/v1/user-groups", body).get("id").asText();
  }

  String uniqueName(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /** Crea un grupo con permisos y concesiones, un usuario que lo integra y devuelve su cookie. */
  String userCookie(List<String> permissions, List<? extends Map<String, ?>> grants)
      throws IOException {
    String groupId = createGroup(uniqueName("grupo"), permissions, grants);
    String username = uniqueName("usuario");
    createUser(username, List.of(groupId));
    return loginAs(username, USER_PASSWORD);
  }

  /** Crea un grupo con los permisos de un programador y sin concesiones. */
  String schedulerGroup() throws IOException {
    return createGroup(uniqueName("programadores"), SCHEDULER_PERMISSIONS, List.of());
  }

  List<String> fieldNames(JsonNode node) {
    return node.properties().stream().map(Map.Entry::getKey).toList();
  }
}
