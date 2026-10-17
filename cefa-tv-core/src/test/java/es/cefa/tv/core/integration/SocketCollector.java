package es.cefa.tv.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Manejador de pruebas que acumula los mensajes de texto y el cierre de una conexión. */
final class SocketCollector extends TextWebSocketHandler {

  final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
  volatile CloseStatus closeStatus;
  volatile WebSocketSession session;

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    messages.add(message.getPayload());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    closeStatus = status;
  }

  String next() throws InterruptedException {
    String message = messages.poll(10, TimeUnit.SECONDS);
    assertThat(message).as("mensaje recibido").isNotNull();
    return message;
  }

  String nextContaining(String fragment) throws InterruptedException {
    for (int i = 0; i < 20; i++) {
      String message = next();
      if (message.contains(fragment)) {
        return message;
      }
    }
    throw new AssertionError("No llegó un mensaje con " + fragment);
  }

  void awaitClosed() throws InterruptedException {
    for (int i = 0; i < 100 && closeStatus == null; i++) {
      Thread.sleep(100);
    }
    assertThat(closeStatus).as("conexión cerrada").isNotNull();
  }
}
