package es.cefa.tv.core.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PlayerSessionRegistryBridgeTest {

  private final UUID virtual = UUID.randomUUID();
  private VirtualPlayerBridge bridge;
  private PlayerSessionRegistry registry;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    bridge = mock(VirtualPlayerBridge.class);
    when(bridge.handles(virtual)).thenReturn(true);
    ObjectProvider<VirtualPlayerBridge> provider = mock(ObjectProvider.class);
    when(provider.orderedStream()).thenAnswer(call -> Stream.of(bridge));
    registry = new PlayerSessionRegistry(provider);
  }

  @Test
  void messagesForAVirtualPlayerGoToItsBridge() {
    boolean sent = registry.sendToPlayer(virtual, "{\"type\":\"REMOTE_START\"}");

    assertThat(sent).isTrue();
    verify(bridge).onMessage(virtual, "{\"type\":\"REMOTE_START\"}");
  }

  @Test
  void aVirtualPlayerIsConnectedWhileItsBridgeSaysItIsOnline() {
    when(bridge.isOnline(virtual)).thenReturn(true);
    assertThat(registry.isConnected(virtual)).isTrue();

    when(bridge.isOnline(virtual)).thenReturn(false);
    assertThat(registry.isConnected(virtual)).isFalse();
  }

  @Test
  void playersNobodyHandlesStayUnreachable() {
    UUID other = UUID.randomUUID();

    assertThat(registry.sendToPlayer(other, "{}")).isFalse();
    assertThat(registry.isConnected(other)).isFalse();
    verify(bridge, never()).onMessage(any(), any());
  }

  @Test
  void aRegistryWithoutBridgesBehavesAsBefore() {
    PlayerSessionRegistry plain = new PlayerSessionRegistry();

    assertThat(plain.sendToPlayer(virtual, "{}")).isFalse();
    assertThat(plain.isConnected(virtual)).isFalse();
  }
}
