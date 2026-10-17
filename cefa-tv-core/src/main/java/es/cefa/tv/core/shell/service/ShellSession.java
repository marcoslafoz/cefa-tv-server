package es.cefa.tv.core.shell.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.socket.WebSocketSession;

/** Estado en memoria de una sesión de terminal remota activa. */
final class ShellSession {

  final String id = UUID.randomUUID().toString();
  final UUID playerId;
  final WebSocketSession viewer;
  final UUID auditId;
  final Instant openedAt = Instant.now();
  final AtomicLong bytesIn = new AtomicLong();
  final AtomicLong bytesOut = new AtomicLong();
  volatile Instant lastInputAt = openedAt;

  ShellSession(UUID playerId, WebSocketSession viewer, UUID auditId) {
    this.playerId = playerId;
    this.viewer = viewer;
    this.auditId = auditId;
  }
}
