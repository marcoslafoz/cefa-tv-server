# Datos del proyecto

Datos de referencia sobre CEFA TV Server (URLs, servidores, sistemas externos...). Este fichero es la única fuente de estos datos: los demás documentos lo enlazan en vez de copiarlos.

Nunca escribas aquí contraseñas, tokens, claves privadas ni cualquier otro secreto. Para cada credencial indica solo dónde vive.

## Entornos

| Entorno | URL |
|---|---|
| Producción | Pendiente de definir |

## Notion

| Elemento | Enlace |
|---|---|
| Carpeta del proyecto (`CEFA Informática / Projects / CEFA TV`; cubre el servidor, el reproductor y la integración con Aracast) | https://app.notion.com/p/3fea765c61d981f09db5f8faad8c2c86 |
| Kanban | https://app.notion.com/p/3fea765c61d980a99356c5439ebf6b87 |
| Documentación (incluye el manual de usuario) | https://app.notion.com/p/3fea765c61d9815e9ab3c6f4b33595ba |

## Servidores

| Entorno | Host / IP | Usuario | Acceso | Ruta del proyecto | Servicios |
|---|---|---|---|---|---|
| Producción | `10.21.35.133` (Debian 13) | `root` | SSH con contraseña; la contraseña está en el gestor de contraseñas de CEFA, no en el repositorio | `/root/cefa-tv-server` | Contenedores `cefa-tv-core-app`, `cefa-tv-core-db`, `cefa-tv-core-garage`, `cefa-tv-core-discovery`, `cefa-tv-app-app` y `cefa-tv-app-nginx`. Web en el puerto 4000 (detrás de `tv.cefa.es`) y API del core en el 8080. |

## Sistemas externos

- **Players Aracast** (Aranova, `AraCastPlayer`), que sustituyen a la antigua herramienta Channel 6. El servidor los gestiona por TCP 5401, FTP 5481, SSH y UDP 30333 (ver "Reproductores Aracast" en [`architecture.md`](./architecture.md)). Hay un **banco de pruebas** (`10.21.34.33`, AraCastPlayer 2.6.19, sin pantalla conectada) donde se puede publicar, reprogramar y reiniciar libremente, y players de **producción** (por ejemplo el de oficinas, `10.21.35.165`) en los que solo se hacen operaciones de lectura salvo autorización expresa.
- **Servidores NTP internos** `10.21.34.10` y `10.21.35.2`, con los que se sincroniza la hora de los Aracast (`ARACAST_NTP_SERVERS`).

## Credenciales y secretos

Los secretos del servidor (contraseña de PostgreSQL, claves S3 y secreto RPC de Garage, `JWT_SECRET`, contraseña del administrador inicial y los datos del registro de Microsoft Entra ID: `AZURE_TENANT_ID`, `AZURE_CLIENT_ID` y `AZURE_CLIENT_SECRET`) viven en el fichero `.env` de cada proyecto (no versionado); la plantilla es `.env.template`. El reproductor guarda su token de dispositivo en el volumen `/data` del contenedor.

Las contraseñas SSH (`root`) y FTP de cada Aracast se guardan cifradas en la base de datos y se cambian desde la ficha del player; `ARACAST_SSH_PASSWORDS`, `ARACAST_FTP_PASSWORD` y `ARACAST_SECRET_KEY` del `.env` de `cefa-tv-core` son el respaldo común y la clave de cifrado.
