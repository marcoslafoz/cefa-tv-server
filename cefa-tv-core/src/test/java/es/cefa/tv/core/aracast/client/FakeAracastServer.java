package es.cefa.tv.core.aracast.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Servidor TCP mínimo que imita el protocolo de un player: una línea JSON por conexión. */
class FakeAracastServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final Thread thread;
  final CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

  FakeAracastServer(Function<String, String> responder) throws IOException {
    serverSocket = new ServerSocket(0);
    thread =
        new Thread(
            () -> {
              while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                  BufferedReader reader =
                      new BufferedReader(
                          new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                  String line = reader.readLine();
                  received.add(line);
                  OutputStream out = socket.getOutputStream();
                  out.write((responder.apply(line) + "\r\n").getBytes(StandardCharsets.UTF_8));
                  out.flush();
                } catch (IOException e) {
                  return;
                }
              }
            });
    thread.setDaemon(true);
    thread.start();
  }

  int port() {
    return serverSocket.getLocalPort();
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
  }
}
