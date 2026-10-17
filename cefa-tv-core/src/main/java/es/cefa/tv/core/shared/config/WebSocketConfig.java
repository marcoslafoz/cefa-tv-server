package es.cefa.tv.core.shared.config;

import es.cefa.tv.core.preview.websocket.PreviewHandshakeInterceptor;
import es.cefa.tv.core.preview.websocket.PreviewViewerHandler;
import es.cefa.tv.core.realtime.PlayerHandshakeInterceptor;
import es.cefa.tv.core.realtime.PlayerSocketHandler;
import es.cefa.tv.core.realtime.RemoteHandshakeInterceptor;
import es.cefa.tv.core.realtime.RemoteViewerHandler;
import es.cefa.tv.core.shell.websocket.ShellHandshakeInterceptor;
import es.cefa.tv.core.shell.websocket.ShellViewerHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/** Registro de los endpoints WebSocket de reproductores, visores remotos y terminales. */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private static final int MAX_MESSAGE_BYTES = 8 * 1024 * 1024;

  private final PlayerSocketHandler playerHandler;
  private final RemoteViewerHandler remoteHandler;
  private final PlayerHandshakeInterceptor playerInterceptor;
  private final RemoteHandshakeInterceptor remoteInterceptor;
  private final ShellViewerHandler shellHandler;
  private final ShellHandshakeInterceptor shellInterceptor;
  private final PreviewViewerHandler previewHandler;
  private final PreviewHandshakeInterceptor previewInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(playerHandler, "/ws/player")
        .addInterceptors(playerInterceptor)
        .setAllowedOriginPatterns("*");
    registry
        .addHandler(remoteHandler, "/ws/remote/*")
        .addInterceptors(remoteInterceptor)
        .setAllowedOriginPatterns("*");
    registry
        .addHandler(shellHandler, "/ws/shell/*")
        .addInterceptors(shellInterceptor)
        .setAllowedOriginPatterns("*");
    registry
        .addHandler(previewHandler, "/ws/previews")
        .addInterceptors(previewInterceptor)
        .setAllowedOriginPatterns("*");
  }

  /**
   * Amplía los búferes del contenedor para admitir fotogramas JPEG.
   *
   * @return la configuración del contenedor WebSocket
   */
  @Bean
  public ServletServerContainerFactoryBean webSocketContainer() {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
    container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BYTES);
    container.setMaxSessionIdleTimeout(0L);
    return container;
  }
}
