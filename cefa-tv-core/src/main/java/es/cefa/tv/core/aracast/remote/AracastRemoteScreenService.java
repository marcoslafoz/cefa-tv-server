package es.cefa.tv.core.aracast.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import es.cefa.tv.core.aracast.client.AracastSshClient;
import es.cefa.tv.core.aracast.exception.AracastException;
import es.cefa.tv.core.aracast.service.AracastCaptureService;
import es.cefa.tv.core.aracast.service.AracastQueryService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Pantalla en vivo de un Aracast y control de su ratón y teclado. Mantiene una sola sesión SSH por
 * player: por un canal sale la captura continua en MJPEG y por otros canales cortos entran los
 * eventos, que se inyectan con XTest desde Python (los players no tienen xdotool).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AracastRemoteScreenService {

  /** Ancho de la imagen que se envía al navegador. */
  private static final int FRAME_WIDTH = 1280;

  private static final int FRAMES_PER_SECOND = 4;
  private static final long IDLE_MILLIS = 20_000;
  private static final long FRAME_WAIT_MILLIS = 2_500;
  private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;
  private static final String ENV = "export DISPLAY=:0 XAUTHORITY=/root/.Xauthority; ";

  private static final String INPUT_SCRIPT =
      """
      import ctypes, json, sys, time
      x = ctypes.CDLL('libX11.so.6')
      t = ctypes.CDLL('libXtst.so.6')
      V = ctypes.c_void_p
      x.XOpenDisplay.restype = V
      x.XOpenDisplay.argtypes = [ctypes.c_char_p]
      x.XStringToKeysym.restype = ctypes.c_ulong
      x.XStringToKeysym.argtypes = [ctypes.c_char_p]
      x.XKeysymToKeycode.restype = ctypes.c_ubyte
      x.XKeysymToKeycode.argtypes = [V, ctypes.c_ulong]
      x.XKeycodeToKeysym.restype = ctypes.c_ulong
      x.XKeycodeToKeysym.argtypes = [V, ctypes.c_ubyte, ctypes.c_int]
      x.XFlush.argtypes = [V]
      t.XTestFakeKeyEvent.argtypes = [V, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
      t.XTestFakeMotionEvent.argtypes = [V, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_ulong]
      t.XTestFakeButtonEvent.argtypes = [V, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
      x.XDefaultRootWindow.restype = ctypes.c_ulong
      x.XDefaultRootWindow.argtypes = [V]
      x.XGetInputFocus.argtypes = [V, ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.c_int)]
      x.XSetInputFocus.argtypes = [V, ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
      x.XQueryPointer.argtypes = [V, ctypes.c_ulong] + [ctypes.POINTER(ctypes.c_ulong)] * 2 + [ctypes.POINTER(ctypes.c_int)] * 4 + [ctypes.POINTER(ctypes.c_uint)]
      HANDLER = ctypes.CFUNCTYPE(ctypes.c_int, V, V)
      ignore = HANDLER(lambda display, event: 0)
      x.XSetErrorHandler(ignore)
      d = x.XOpenDisplay(None)
      if not d:
          sys.exit('Sin acceso a la pantalla')
      def focus(force):
          cur = ctypes.c_ulong()
          rev = ctypes.c_int()
          x.XGetInputFocus(d, ctypes.byref(cur), ctypes.byref(rev))
          if not force and cur.value not in (0, 1):
              return
          r = ctypes.c_ulong()
          top = ctypes.c_ulong()
          ints = [ctypes.c_int() for _ in range(4)]
          m = ctypes.c_uint()
          x.XQueryPointer(d, x.XDefaultRootWindow(d), ctypes.byref(r), ctypes.byref(top), *[ctypes.byref(i) for i in ints], ctypes.byref(m))
          if top.value and top.value != cur.value:
              x.XSetInputFocus(d, top.value, 2, 0)
              x.XFlush(d)
              time.sleep(0.05)
      MODS = {'ctrl': 'Control_L', 'alt': 'Alt_L', 'shift': 'Shift_L', 'super': 'Super_L'}
      def code(sym):
          return x.XKeysymToKeycode(d, sym)
      def key(kc, down):
          t.XTestFakeKeyEvent(d, kc, 1 if down else 0, 0)
      def sym_of(name):
          return x.XStringToKeysym(name.encode())
      def press(sym, mods=()):
          kc = code(sym)
          if not kc:
              return
          held = [code(sym_of(m)) for m in mods]
          lvl = [x.XKeycodeToKeysym(d, kc, i) for i in range(4)]
          if not held:
              if lvl[0] != sym and lvl[1] == sym:
                  held = [code(sym_of('Shift_L'))]
              elif lvl[0] != sym and lvl[2] == sym:
                  held = [code(sym_of('ISO_Level3_Shift'))]
              elif lvl[0] != sym and lvl[3] == sym:
                  held = [code(sym_of('ISO_Level3_Shift')), code(sym_of('Shift_L'))]
          for h in held:
              key(h, True)
          key(kc, True)
          key(kc, False)
          for h in reversed(held):
              key(h, False)
          x.XFlush(d)
          time.sleep(0.01)
      def char_sym(ch):
          o = ord(ch)
          if ch == '\\n':
              return sym_of('Return')
          if ch == '\\t':
              return sym_of('Tab')
          return o if o < 0x100 else 0x01000000 + o
      def move(px, py):
          t.XTestFakeMotionEvent(d, -1, px, py, 0)
          x.XFlush(d)
          time.sleep(0.03)
      def button(b, n=1):
          for _ in range(n):
              t.XTestFakeButtonEvent(d, b, 1, 0)
              t.XTestFakeButtonEvent(d, b, 0, 0)
              x.XFlush(d)
              time.sleep(0.04)
      a = json.loads(sys.argv[1])
      kind = a['type']
      if kind == 'click':
          move(a['x'], a['y'])
          button(a.get('button', 1), a.get('count', 1))
          focus(True)
      elif kind == 'scroll':
          move(a['x'], a['y'])
          n = a.get('count', 1)
          button(4 if n < 0 else 5, abs(n))
      elif kind == 'key':
          focus(False)
          parts = a['key'].split('+')
          mods = [MODS[p.lower()] for p in parts[:-1] if p.lower() in MODS]
          press(sym_of(parts[-1]), mods)
      elif kind == 'text':
          focus(False)
          for ch in a['text']:
              press(char_sym(ch))
      x.XFlush(d)
      """;

  private final AracastSshClient ssh;
  private final AracastQueryService queryService;
  private final ObjectMapper mapper;
  private final ConcurrentMap<String, Screen> screens = new ConcurrentHashMap<>();

  /** Última captura más nueva que {@code after}, o vacío si no llega ninguna a tiempo. */
  public Optional<Frame> frame(String host, long after) {
    Screen screen = screenFor(host);
    screen.touch();
    long deadline = System.currentTimeMillis() + FRAME_WAIT_MILLIS;
    synchronized (screen) {
      while (screen.sequence <= after && screen.alive) {
        long left = deadline - System.currentTimeMillis();
        if (left <= 0) {
          break;
        }
        try {
          screen.wait(left);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      if (screen.latest == null || screen.sequence <= after) {
        return Optional.empty();
      }
      return Optional.of(new Frame(screen.latest, screen.sequence));
    }
  }

  /** Inyecta una acción de ratón o teclado en la pantalla del player. */
  public void input(String host, RemoteInput request) {
    Screen screen = screenFor(host);
    screen.touch();
    Map<String, Object> action = new LinkedHashMap<>();
    action.put("type", request.type());
    switch (request.type()) {
      case "click", "scroll" -> {
        if (request.x() == null || request.y() == null) {
          throw new IllegalArgumentException("Falta la posición");
        }
        action.put("x", (int) Math.round(request.x() * (screen.width - 1)));
        action.put("y", (int) Math.round(request.y() * (screen.height - 1)));
        action.put("button", request.button() == null ? 1 : request.button());
        action.put("count", request.count() == null ? 1 : request.count());
      }
      case "key" -> {
        if (request.key() == null) {
          throw new IllegalArgumentException("Falta la tecla");
        }
        action.put("key", request.key());
      }
      default -> {
        if (request.text() == null || request.text().isEmpty()) {
          throw new IllegalArgumentException("Falta el texto");
        }
        action.put("text", request.text());
      }
    }
    runInput(screen, action);
  }

  /** Cierra la sesión del player, si hay. */
  public void stop(String host) {
    Screen screen = screens.remove(host.toLowerCase());
    if (screen != null) {
      screen.close();
    }
  }

  @Scheduled(fixedDelay = 5000)
  void closeIdle() {
    long now = System.currentTimeMillis();
    screens.entrySet().removeIf(entry -> closeIfStale(entry.getValue(), now));
  }

  private boolean closeIfStale(Screen screen, long now) {
    if (!screen.alive || now - screen.lastUse > IDLE_MILLIS) {
      screen.close();
      return true;
    }
    return false;
  }

  private Screen screenFor(String host) {
    String key = host.toLowerCase();
    Screen current = screens.get(key);
    if (current != null && current.alive) {
      return current;
    }
    synchronized (screens) {
      current = screens.get(key);
      if (current != null && current.alive) {
        return current;
      }
      if (current != null) {
        current.close();
      }
      Screen created = start(host);
      screens.put(key, created);
      return created;
    }
  }

  private Screen start(String host) {
    String size = size(host);
    String[] wh = size.split("x");
    Session session = ssh.open(host, 60);
    Screen screen = new Screen(session, Integer.parseInt(wh[0]), Integer.parseInt(wh[1]));
    try {
      ChannelExec channel = (ChannelExec) session.openChannel("exec");
      channel.setCommand(
          ENV
              + "exec ffmpeg -loglevel error -f x11grab -framerate "
              + FRAMES_PER_SECOND
              + " -video_size "
              + size
              + " -i :0 -vf scale="
              + FRAME_WIDTH
              + ":-2 -q:v 6 -f image2pipe -c:v mjpeg -");
      channel.setInputStream(null);
      channel.setErrStream(System.err, true);
      InputStream in = channel.getInputStream();
      channel.connect(10_000);
      screen.channel = channel;
      Thread reader = new Thread(() -> pump(screen, in, host), "remote-screen-" + host);
      reader.setDaemon(true);
      reader.start();
    } catch (JSchException | IOException e) {
      screen.close();
      throw new AracastException("No se pudo abrir la pantalla de " + host, e);
    }
    return screen;
  }

  private String size(String host) {
    try {
      return AracastCaptureService.parseSize(queryService.getStatus(host).resolution());
    } catch (RuntimeException e) {
      return AracastCaptureService.parseSize(null);
    }
  }

  private void pump(Screen screen, InputStream in, String host) {
    byte[] chunk = new byte[64 * 1024];
    byte[] data = new byte[MAX_FRAME_BYTES];
    int length = 0;
    try {
      int read;
      while (screen.alive && (read = in.read(chunk)) != -1) {
        if (length + read > data.length) {
          length = 0;
        }
        System.arraycopy(chunk, 0, data, length, read);
        length += read;
        length = emitFrames(screen, data, length);
      }
    } catch (IOException e) {
      log.debug("Captura de {} cerrada: {}", host, e.getMessage());
    } finally {
      screen.close();
    }
  }

  /** Extrae del búfer los JPEG completos (de FFD8 a FFD9) y devuelve los bytes que sobran. */
  private int emitFrames(Screen screen, byte[] data, int length) {
    int start = 0;
    while (true) {
      int soi = indexOf(data, start, length, (byte) 0xFF, (byte) 0xD8);
      if (soi < 0) {
        start = length > 0 && data[length - 1] == (byte) 0xFF ? length - 1 : length;
        break;
      }
      int eoi = indexOf(data, soi + 2, length, (byte) 0xFF, (byte) 0xD9);
      if (eoi < 0) {
        start = soi;
        break;
      }
      byte[] jpeg = new byte[eoi + 2 - soi];
      System.arraycopy(data, soi, jpeg, 0, jpeg.length);
      screen.publish(jpeg);
      start = eoi + 2;
    }
    int rest = length - start;
    if (start > 0 && rest > 0) {
      System.arraycopy(data, start, data, 0, rest);
    }
    return rest;
  }

  private static int indexOf(byte[] data, int from, int to, byte a, byte b) {
    for (int i = from; i < to - 1; i++) {
      if (data[i] == a && data[i + 1] == b) {
        return i;
      }
    }
    return -1;
  }

  private void runInput(Screen screen, Map<String, Object> action) {
    String json;
    try {
      json = mapper.writeValueAsString(action);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Acción no válida", e);
    }
    Base64.Encoder b64 = Base64.getEncoder();
    String script = b64.encodeToString(INPUT_SCRIPT.getBytes(StandardCharsets.UTF_8));
    String arg = b64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    String command =
        ENV
            + "python3 -c \"$(echo "
            + script
            + " | base64 -d)\" \"$(echo "
            + arg
            + " | base64 -d)\"";
    List<ChannelExec> opened = new ArrayList<>();
    try {
      synchronized (screen.inputLock) {
        ChannelExec channel = (ChannelExec) screen.session.openChannel("exec");
        opened.add(channel);
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        channel.setCommand(command);
        channel.setInputStream(null);
        channel.setErrStream(err);
        channel.connect(5_000);
        long deadline = System.currentTimeMillis() + 8_000;
        while (!channel.isClosed() && System.currentTimeMillis() < deadline) {
          Thread.sleep(15);
        }
        if (channel.getExitStatus() != 0) {
          throw new AracastException(
              "No se pudo enviar la acción: " + err.toString(StandardCharsets.UTF_8).trim());
        }
      }
    } catch (JSchException e) {
      throw new AracastException("Error SSH enviando la acción: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      opened.forEach(ChannelExec::disconnect);
    }
  }

  /**
   * Imagen de la pantalla.
   *
   * @param jpeg bytes JPEG
   * @param sequence número de imagen, creciente
   */
  public record Frame(byte[] jpeg, long sequence) {}

  private static final class Screen {
    final Session session;
    final int width;
    final int height;
    final Object inputLock = new Object();
    volatile ChannelExec channel;
    volatile boolean alive = true;
    volatile long lastUse = System.currentTimeMillis();
    byte[] latest;
    long sequence;

    Screen(Session session, int width, int height) {
      this.session = session;
      this.width = width;
      this.height = height;
    }

    void touch() {
      lastUse = System.currentTimeMillis();
    }

    synchronized void publish(byte[] jpeg) {
      latest = jpeg;
      sequence++;
      notifyAll();
    }

    synchronized void close() {
      alive = false;
      notifyAll();
      try {
        if (channel != null) {
          channel.disconnect();
        }
      } finally {
        session.disconnect();
      }
    }
  }
}
