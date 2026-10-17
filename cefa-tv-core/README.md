# CEFA TV Core

Backend del emisor de CEFA TV (Spring Boot 3.4, Java 21, PostgreSQL, MinIO). Gestiona la biblioteca de medios, las playlists, la programación y la entrega a los reproductores. El contrato de APIs, el protocolo y el modelo de datos están en [`../docs/llm/context/architecture.md`](../docs/llm/context/architecture.md).

## Arranque con Docker

```bash
cp .env.template .env
make up
```

Antes de arrancar, rellena en `.env` como mínimo `JWT_SECRET` (32 caracteres o más), `ADMIN_PASSWORD`, `DB_PASSWORD` y `MINIO_SECRET_KEY`. `make up` crea la red `cefa-tv-net` y levanta cuatro contenedores:

| Servicio | Función | Puerto |
|---|---|---|
| `app` | API del core; los reproductores se conectan aquí | `8080` |
| `db` | PostgreSQL 16 | `127.0.0.1:5432` |
| `minio` | Almacenamiento de objetos (API y consola) | `9000`, `9001` |
| `discovery` | Sidecar en red host que responde al descubrimiento UDP | `47808/udp` |

La imagen de MinIO se toma de `MINIO_IMAGE` (por defecto `quay.io/minio/minio:latest`, la distribución oficial actual).

## Desarrollo local

Necesita PostgreSQL y MinIO accesibles, y `ffmpeg` / `ffprobe` en el `PATH` para duración y miniaturas de vídeo (si no están, el vídeo se sube igualmente sin esos datos).

```bash
make run
```

El perfil `dev` activa los logs de depuración. La documentación OpenAPI está en `/swagger-ui.html`.

## Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/cefatv`, `cefatv`, `cefatv` | Conexión a PostgreSQL. |
| `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` | `http://localhost:9000`, `cefatv`, `cefatv-secret`, `cefa-tv-media` | Conexión a MinIO; el bucket se crea al arrancar. |
| `JWT_SECRET` | sin valor útil | Secreto de firma de la sesión (32 caracteres o más). |
| `COOKIE_SECURE` | `false` | Pon `true` si se sirve por HTTPS. |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | vacíos | Crean el primer administrador si no hay usuarios. |
| `PUBLIC_URL` | vacío | URL del core visible por los reproductores; la usa el sidecar de descubrimiento en lugar de calcularla. |
| `APP_BASE_URL` | `http://localhost:4000` | URL pública de la interfaz web; destino de las redirecciones tras iniciar sesión con Microsoft. |
| `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET` | vacíos | Registro de la aplicación en Microsoft Entra ID. Mientras falte alguno el inicio de sesión con Microsoft está apagado. |
| `AZURE_REDIRECT_URI` | vacío | URI de redirección que se envía a Microsoft; vacío usa `{baseUrl}/login/oauth2/code/{registrationId}`. Detrás de nginx pon la pública completa. |
| `MICROSOFT_AUTO_PROVISION` | `false` | Si es `true`, el primer acceso de un usuario de Microsoft sin cuenta la crea sin permisos ni grupos (ver `MICROSOFT_DEFAULT_GROUP_ID`). |
| `MICROSOFT_DEFAULT_GROUP_ID` | vacío | Identificador de un grupo de usuarios al que se añaden los usuarios creados automáticamente. Nunca puede ser un grupo con acceso total; si lo es o no existe, el usuario se crea sin grupos. |
| `SERVER_PORT` | `8080` | Puerto publicado por Docker. |
| `FFMPEG_PATH`, `FFPROBE_PATH` | `ffmpeg`, `ffprobe` | Ejecutables de procesado de vídeo. |
| `MEDIA_TEMP_DIR` | temporal del sistema | Directorio donde se procesan las subidas. |

## Estructura

El código vive en `es.cefa.tv.core` organizado por dominio: `auth`, `microsoft`, `media`, `playlist`, `group`, `player`, `pairing`, `role`, `usergroup`, `authorization`, `schedule`, `manifest`, `delivery`, `playerapi`, `realtime` (WebSocket), `telemetry`, `shell`, `dashboard`, `publicinfo` y `shared`.

- Cualquier cambio de contenido publica un `ContentChangedEvent`; `ManifestRebuildCoordinator` espera 2 s sin cambios y recalcula el manifest de cada reproductor activo. Si el hash del contenido cambia se crea una versión nueva, se encola su entrega y se envía `SYNC` por WebSocket.
- Un reproductor desconectado conserva `desiredVersion` mayor que `appliedVersion` (`pending`) hasta que vuelve y la aplica: el heartbeat o el saludo `HELLO` por WebSocket cierran la entrega.
- Las órdenes `RELOAD`, `RESTART`, `UNENROLL`, `DISPLAY_REDETECT` y `SET_RESOLUTION` (esta con `output`, `width` y `height`) se envían por WebSocket y, si el reproductor no está conectado, se encolan y se devuelven en el siguiente heartbeat.
- Telemetría: `system` y `displays` llegan en el heartbeat y en el mensaje WebSocket `TELEMETRY`; se guardan en `players` y una muestra por reproductor cada 10 s como mínimo en `player_metrics` (se purga a las 24 h). `GET /api/v1/players/{id}/metrics?hours=` devuelve la serie.
- Terminal remota: `/ws/shell/{playerId}` (permiso `PLAYER_TERMINAL` con acceso `OPERATE` sobre el grupo del reproductor) retransmite una sesión de shell al reproductor por su WebSocket saliente, con una sesión por reproductor, cierre a los 15 min sin entrada (`cefa.tv.shell.idle-timeout-sec`) o a las 2 h (`max-duration-sec`) y auditoría en `shell_audits` (`GET /api/v1/players/{id}/shell-audits`, mismo permiso); no se guarda el contenido.
- Emparejamiento: el reproductor pide un código con `POST /player-api/pairing/request` (formato `ABC-123`, caduca a los 15 min), lo muestra en pantalla y consulta `GET /player-api/pairing/{id}` con `X-Pairing-Secret`. Un usuario con `PLAYER_MANAGE` lo introduce en `POST /api/v1/pairings/claim` (máximo 5 códigos erróneos por minuto y usuario). Mientras el reproductor no use su token, cada consulta aprobada entrega un token nuevo, para que una respuesta perdida no lo deje sin credenciales.
- Permisos: `AccessService` y `AccessPolicy` son el único punto de decisión. Los endpoints se anotan con `@RequiresPermission` (cualquiera de los permisos indicados) y `@RequiresScope` (nivel `VIEW` u `OPERATE` sobre el reproductor o grupo que identifica una variable de la ruta); los filtros de listados y las comprobaciones de destino de una programación viven en sus servicios. No hay roles: la autorización sale de los grupos de usuarios (ver "Usuarios y grupos de usuarios"). Un reproductor sin grupo ni sede solo lo gestiona un administrador.

## Pruebas

```bash
make test
```

Las pruebas de integración usan Testcontainers (PostgreSQL y MinIO) y se saltan si no hay Docker. El plugin de Surefire fija `api.version=1.44` para que docker-java hable con Docker Engine 29. La imagen de MinIO de las pruebas se cambia con `-Dtest.minio.image=...`.

## Inicio de sesión con Microsoft (híbrido)

Los usuarios pueden ser `LOCAL` (usuario y contraseña), `MICROSOFT` (solo Microsoft Entra ID, sin contraseña) o `HYBRID` (ambas formas). La función viene apagada: sin las variables `AZURE_*` la aplicación arranca igual y `GET /api/v1/auth/providers` (público) devuelve `{"local":true,"microsoft":false}`. La interfaz usa ese endpoint para mostrar o no el botón de Microsoft.

### Cómo funciona

1. El navegador va a `/core-api/oauth2/authorization/azure` (opcionalmente con `?redirect=/es/schedule&locale=es`). El destino solo se acepta si es una ruta relativa del mismo sitio; cualquier otra cosa se ignora y se usa el panel.
2. Microsoft autentica y devuelve el control a `/login/oauth2/code/azure`.
3. El core busca al usuario local por el identificador de objeto (`oid`) ya enlazado y, si no lo hay, por correo sin distinguir mayúsculas (`email`, `preferred_username` o `upn`). En el primer acceso guarda el `oid` en la cuenta; desde entonces un cambio de correo en Microsoft no permite suplantarla.
4. Si el usuario existe, está activo y es `MICROSOFT` o `HYBRID`, se emite la misma cookie `ctv_session` del acceso normal y se redirige a la interfaz. Si no, se redirige a `<APP_BASE_URL>/login?error=<código>`.

Códigos de `?error=`: `microsoft_user_not_found`, `microsoft_user_disabled`, `microsoft_not_allowed` (el usuario es `LOCAL`), `microsoft_account_mismatch`, `microsoft_email_missing`, `microsoft_cancelled` y `microsoft_failed`. Un usuario `LOCAL` con el mismo correo no se enlaza: un administrador debe pasarlo a `HYBRID` o `MICROSOFT`.

Con `MICROSOFT_AUTO_PROVISION=true` un usuario de Microsoft sin cuenta se crea solo, con nombre de usuario igual al correo y sin grupos, de modo que no ve nada hasta que un administrador lo añada a un grupo. Con `MICROSOFT_DEFAULT_GROUP_ID` se le añade a ese grupo (nunca uno con acceso total).

### Registrar la aplicación en Entra ID

1. En el portal de Azure abre **Microsoft Entra ID, Registros de aplicaciones, Nuevo registro**.
2. Nombre `CEFA TV`. Tipos de cuenta compatibles: **Solo cuentas de este directorio organizativo (inquilino único)**. No uses multiinquilino: el core exige un tenant concreto para validar el emisor del token.
3. URI de redirección, plataforma **Web**: `https://<host>/core-api/login/oauth2/code/azure`. Para pruebas en local añade también `http://localhost:4000/core-api/login/oauth2/code/azure`. La URI debe coincidir exactamente con `AZURE_REDIRECT_URI`.
4. En **Información general** copia el **Id. de aplicación (cliente)** y el **Id. de directorio (inquilino)**. El inquilino debe ser el GUID, no el dominio.
5. En **Certificados y secretos, Nuevo secreto de cliente**, crea un secreto y copia su **valor** (solo se muestra una vez).
6. En **Permisos de API** basta con los delegados de Microsoft Graph `openid`, `profile` y `email`; no hacen falta permisos de aplicación. Si la organización lo exige, concede el consentimiento del administrador.
7. Opcional, **Configuración de token, Agregar notificación opcional, ID**: añade `email` para que el token traiga el correo; sin ello se usa `preferred_username`.

### Variables que hay que rellenar

En `cefa-tv-core/.env` (los secretos no se versionan):

```
APP_BASE_URL=https://<host>
AZURE_TENANT_ID=<id de directorio, GUID>
AZURE_CLIENT_ID=<id de aplicación>
AZURE_CLIENT_SECRET=<valor del secreto>
AZURE_REDIRECT_URI=https://<host>/core-api/login/oauth2/code/azure
COOKIE_SECURE=true
MICROSOFT_AUTO_PROVISION=false
MICROSOFT_DEFAULT_GROUP_ID=
```

Después ejecuta `make rebuild`. El flujo solo se activa si `AZURE_TENANT_ID` es un GUID y `AZURE_CLIENT_ID` y `AZURE_CLIENT_SECRET` tienen valor real. Los valores que empiezan por `changeme` se tratan como vacíos. El core ya envía `X-Forwarded-*` con `server.forward-headers-strategy=framework`; el nginx de `cefa-tv-app` proxifica `/core-api/` al core, que recibe la ruta sin ese prefijo.

### Administrar usuarios

`POST /api/v1/users` y `PUT /api/v1/users/{id}` aceptan `email` y `authProvider`. Un usuario `MICROSOFT` necesita correo y no admite contraseña; `LOCAL` y `HYBRID` necesitan contraseña. Para pasar un usuario de `MICROSOFT` a `LOCAL` o `HYBRID` el `PUT` debe incluir `password`. `POST /api/v1/users/{id}/password` y `POST /api/v1/auth/password` devuelven 400 `PASSWORD_NOT_ALLOWED` en usuarios `MICROSOFT`. Con Microsoft sin configurar no se puede pasar a solo Microsoft al último administrador activo que entra con contraseña (409 `LAST_PASSWORD_ADMIN`), y las reglas del último administrador activo siguen vigentes. Los correos son únicos sin distinguir mayúsculas (409 `EMAIL_EXISTS`).

## Auditoría de la actividad de los usuarios

Toda operación de escritura de la API de administración, los accesos denegados y los hechos relevantes de seguridad quedan en la tabla `audit_logs`. La captura es central: un interceptor (`AuditInterceptor`) deduce la acción de la ruta y el método (`POST /players/{id}/restart` es `PLAYER_RESTART`), lee el estado de la entidad antes y después y resume los cambios en `details.changes`. Un endpoint nuevo queda auditado sin tocar nada. Además se registran el login (correcto, fallido y con Microsoft), el logout, el cambio de contraseña, los emparejamientos, las órdenes a pantallas, las sesiones de control remoto y de terminal y los 403.

Nunca se guardan contraseñas, tokens, secretos ni el código de emparejamiento: el saneador (`AuditSanitizer`) elimina esos campos y acota el tamaño de los datos.

| Endpoint | Descripción |
|---|---|
| `GET /api/v1/audit` | Entradas por fecha descendente. Filtros `from`, `to`, `userId`, `action`, `entityType`, `entityId`, `outcome` (`SUCCESS`, `DENIED`, `FAILED`), `q`, `page`, `size` (máximo 200). |
| `GET /api/v1/audit/{id}` | Una entrada. |
| `GET /api/v1/audit/export.csv` | Los mismos filtros, en streaming, hasta 50 000 filas. Neutraliza las fórmulas de hoja de cálculo. La exportación queda auditada (`AUDIT_EXPORT`). |
| `GET /api/v1/audit/stats` | Total, resultados, acciones más frecuentes, usuarios más activos y actividad por día. |
| `GET /api/v1/audit/actions` | Catálogo de acciones para el filtro. |
| `GET/PUT /api/v1/settings/audit` | Días de retención (por defecto 365, mínimo 30). Cambiarla es solo para administradores. |

Hace falta el permiso `AUDIT_VIEW` (lo tienen los grupos con acceso total; cualquier grupo puede incluirlo entre sus permisos). La purga diaria (03:30) borra lo que supera la retención y se audita a sí misma (`AUDIT_PURGE`).

## Carpetas

La biblioteca de medios se organiza en carpetas y subcarpetas (`/api/v1/folders`); las playlists no llevan carpetas. Los nombres son únicos entre carpetas hermanas sin distinguir mayúsculas, la profundidad máxima es 10 y no se admiten ciclos al mover. `GET /media` acepta `folderId` (un identificador o `root`), `recursive=true` y `q`; cada resultado lleva su `folderPath`. Mover una carpeta no cambia las referencias de playlists y programaciones. Crear, mover y borrar carpetas exige `MEDIA_MANAGE`; leerlas, cualquier sesión.

## Sedes y descripción de reproductores

Una sede (`/api/v1/sites`) agrupa reproductores por lugar físico y fija su zona horaria: la del manifest de un reproductor es la de su sede y, si no tiene, la que comunica él mismo. Solo un administrador crea, edita y borra sedes. Una concesión de acceso puede ser sobre un grupo de reproductores o sobre una sede (exactamente una de las dos), y una programación puede apuntar a sedes con `targets.siteIds`. Un reproductor tiene además `description` (hasta 500 caracteres), `siteId` y `preferredOutput` (salida de vídeo que se entrega al reproductor en el heartbeat y por WebSocket).

## Vista previa en vivo

El navegador abre `/ws/previews` y envía `SUBSCRIBE {playerIds, intervalSec}` (hasta 48 reproductores, 5 a 60 segundos). El servidor ordena a cada reproductor empezar a capturar con el primer suscriptor y parar con el último; las suscripciones caducan a los 30 segundos sin renovar. Los fotogramas llegan como mensajes binarios con el identificador del reproductor en 36 bytes ASCII seguido del JPEG, y `GET /api/v1/players/{id}/preview` devuelve el último para el primer pintado. De cada reproductor solo se guarda el último fotograma, con un tope global de memoria. Las métricas `cefatv.preview.frames` están en Actuator.

## Reproductores sin conexión

`neverConnected` marca a los reproductores dados de alta que no se han conectado nunca pasados `NEVER_CONNECTED_FLAG_DAYS` días (7 por defecto); se eliminan solos pasados `NEVER_CONNECTED_DELETE_DAYS` (14).

## Usuarios y grupos de usuarios

No hay roles. Un usuario pertenece a cero o más grupos de usuarios y sus permisos y accesos son la unión de los de sus grupos; sin grupos no ve nada. Un grupo (`/api/v1/user-groups`) define:

- `permissions`: `MEDIA_MANAGE`, `PLAYLIST_MANAGE`, `SCHEDULE_MANAGE`, `PLAYER_VIEW`, `PLAYER_MANAGE`, `PLAYER_REMOTE`, `PLAYER_TERMINAL`, `USER_MANAGE` y `AUDIT_VIEW` (`GET /api/v1/permissions` los lista).
- `grants`: nivel `VIEW` u `OPERATE` sobre un grupo de reproductores (`playerGroupId`) o sobre una sede (`siteId`), exactamente uno de los dos.
- `allAccess`: acceso a todos los reproductores y sedes con todos los permisos. Quien está en un grupo así es administrador. Solo un administrador puede dar o quitar este acceso y cambiar los miembros de estos grupos.
- `memberIds`: los miembros. También se asignan desde `POST/PUT /api/v1/users` con `groupIds`.

El grupo integrado `Administradores` no se puede renombrar, cambiar ni borrar, y no puede quedarse sin su último miembro activo. En general el sistema impide dejar el sistema sin ningún administrador activo (409 `LAST_ADMIN`). Quien gestiona usuarios sin ser administrador no puede dar permisos que no tiene (403 `PRIVILEGE_ESCALATION`).

`GET /api/v1/auth/me` devuelve `{id, username, displayName, email, authProvider, groups, permissions, grants, admin}`. Los permisos efectivos se guardan en memoria unos minutos y se invalidan al editar usuarios, grupos o membresías.

La migración V9 convierte los roles anteriores en grupos (`Administradores`, `Programadores`, `Visualizadores` y un grupo por cada rol propio con usuarios) y las concesiones directas de un usuario en un grupo personal con su nombre.

## Icono y color de los grupos de reproductores

Un grupo de reproductores puede llevar `icon` (nombre de un icono de Tabler en PascalCase sin prefijo, como `IconBuildingStore`) y `color` (`slate`, `blue`, `green`, `amber`, `red`, `violet`, `pink` o `teal`). Al modificar, `null` conserva el valor y una cadena vacía lo quita. Cambiarlos exige `PLAYER_MANAGE`.
