package es.cefa.tv.core.media.service;

import es.cefa.tv.core.shared.config.CoreProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

/** Convierte las páginas de un PDF en imágenes JPEG. */
@Service
@RequiredArgsConstructor
public class PdfRasterizer {

  /** Número máximo de páginas aceptadas por documento. */
  public static final int MAX_PAGES = 300;

  private static final int MAX_PAGE_WIDTH = 2560;

  private final CoreProperties properties;

  /**
   * Resultado del rasterizado.
   *
   * @param pageFiles ficheros JPEG, uno por página
   */
  public record Rasterized(List<Path> pageFiles) {}

  /**
   * Rasteriza todas las páginas del PDF en el directorio indicado.
   *
   * @param pdf fichero PDF
   * @param outputDir directorio donde escribir {@code page-N.jpg}
   * @return los ficheros generados en orden de página
   * @throws IOException si el PDF no se puede leer o supera el máximo de páginas
   */
  public Rasterized rasterize(Path pdf, Path outputDir) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
      int pages = document.getNumberOfPages();
      if (pages == 0 || pages > MAX_PAGES) {
        throw new IOException("Número de páginas no válido: " + pages);
      }
      PDFRenderer renderer = new PDFRenderer(document);
      List<Path> files = new ArrayList<>();
      for (int index = 0; index < pages; index++) {
        BufferedImage rendered =
            renderer.renderImageWithDPI(index, properties.media().pdfDpi(), ImageType.RGB);
        BufferedImage page = ImageToolkit.scaleToWidth(rendered, MAX_PAGE_WIDTH);
        Path target = outputDir.resolve("page-" + index + ".jpg");
        ImageToolkit.writeJpeg(page, target);
        files.add(target);
        if (Files.size(target) == 0) {
          throw new IOException("Página vacía " + index);
        }
      }
      return new Rasterized(files);
    }
  }
}
