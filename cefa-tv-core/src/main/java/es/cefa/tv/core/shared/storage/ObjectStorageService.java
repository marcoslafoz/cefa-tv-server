package es.cefa.tv.core.shared.storage;

import es.cefa.tv.core.shared.config.CoreProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.UploadObjectArgs;
import java.io.InputStream;
import java.nio.file.Path;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

/** Acceso al bucket de MinIO donde se guardan los medios. */
@Service
public class ObjectStorageService implements ApplicationRunner {

  private static final int STARTUP_RETRIES = 20;

  private final MinioClient client;
  private final String bucket;

  /**
   * Crea el servicio.
   *
   * @param properties propiedades de la aplicación
   */
  public ObjectStorageService(CoreProperties properties) {
    CoreProperties.Minio minio = properties.minio();
    this.client =
        MinioClient.builder()
            .endpoint(minio.endpoint())
            .credentials(minio.accessKey(), minio.secretKey())
            .build();
    this.bucket = minio.bucket();
  }

  /**
   * Garantiza que el bucket existe al arrancar, reintentando mientras MinIO termina de iniciarse.
   *
   * @param args argumentos de arranque
   * @throws Exception si MinIO no responde tras los reintentos
   */
  @Override
  public void run(ApplicationArguments args) throws Exception {
    Exception last = null;
    for (int attempt = 0; attempt < STARTUP_RETRIES; attempt++) {
      try {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
          client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        return;
      } catch (Exception e) {
        last = e;
        Thread.sleep(1500);
      }
    }
    throw last;
  }

  /**
   * Sube un fichero local al bucket.
   *
   * @param key clave del objeto
   * @param file fichero local
   * @param contentType tipo MIME
   */
  public void putFile(String key, Path file, String contentType) {
    try {
      client.uploadObject(
          UploadObjectArgs.builder()
              .bucket(bucket)
              .object(key)
              .filename(file.toString())
              .contentType(contentType)
              .build());
    } catch (Exception e) {
      throw new StorageException("No se pudo subir " + key, e);
    }
  }

  /**
   * Abre un objeto completo.
   *
   * @param key clave del objeto
   * @return el flujo de lectura, que debe cerrar quien lo recibe
   */
  public InputStream open(String key) {
    return open(key, 0, null);
  }

  /**
   * Abre un tramo de un objeto.
   *
   * @param key clave del objeto
   * @param offset byte inicial
   * @param length número de bytes o {@code null} hasta el final
   * @return el flujo de lectura, que debe cerrar quien lo recibe
   */
  public InputStream open(String key, long offset, Long length) {
    try {
      GetObjectArgs.Builder builder = GetObjectArgs.builder().bucket(bucket).object(key);
      if (offset > 0 || length != null) {
        builder.offset(offset);
        if (length != null) {
          builder.length(length);
        }
      }
      return client.getObject(builder.build());
    } catch (Exception e) {
      throw new StorageException("No se pudo leer " + key, e);
    }
  }

  /**
   * Borra un objeto; si no existe no hace nada.
   *
   * @param key clave del objeto
   */
  public void delete(String key) {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (Exception e) {
      throw new StorageException("No se pudo borrar " + key, e);
    }
  }
}
