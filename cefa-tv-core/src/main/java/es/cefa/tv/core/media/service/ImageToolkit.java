package es.cefa.tv.core.media.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/** Utilidades de imagen: escalado y escritura de JPEG. */
public final class ImageToolkit {

  private static final float JPEG_QUALITY = 0.85f;

  /** Evita la instanciación de la clase de utilidades. */
  private ImageToolkit() {}

  /**
   * Escala una imagen al ancho indicado conservando la proporción y la aplana sobre blanco.
   *
   * @param source imagen original
   * @param targetWidth ancho de destino; si es mayor que el original no se amplía
   * @return la imagen RGB escalada
   */
  public static BufferedImage scaleToWidth(BufferedImage source, int targetWidth) {
    int width = Math.min(targetWidth, source.getWidth());
    int height = Math.max(1, Math.round((float) source.getHeight() * width / source.getWidth()));
    BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = scaled.createGraphics();
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, width, height);
    graphics.drawImage(source, 0, 0, width, height, null);
    graphics.dispose();
    return scaled;
  }

  /**
   * Escribe una imagen como JPEG de calidad alta.
   *
   * @param image imagen RGB
   * @param target fichero de destino
   * @throws IOException si no se puede escribir
   */
  public static void writeJpeg(BufferedImage image, Path target) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    ImageWriter writer = writers.next();
    ImageWriteParam params = writer.getDefaultWriteParam();
    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    params.setCompressionQuality(JPEG_QUALITY);
    try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), params);
    } finally {
      writer.dispose();
    }
  }
}
