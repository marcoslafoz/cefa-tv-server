# Reproductores Aracast en CEFA TV

Objetivo: dar de alta un player Aracast como un reproductor más de CEFA TV y gestionarlo igual que uno de `cefa-tv-player` (programación, playlists, estado, vista previa, órdenes, control remoto y terminal), de forma transparente para el usuario. Al terminar se retira Channel 6 (`channel-6/`), cuyas funciones se integran aquí.

Decisiones del 19 de octubre de 2026: se llevan todas las funciones de Channel 6 (vista previa y captura por SSH, descubrimiento por red, reiniciar y hora, terminal SSH y cambio de red, control remoto); empezar con playlists homogéneas y soportar después las mixtas con secuencias; retirar Channel 6 al terminar; probar de verdad solo en el banco `ARAPROD03` (`10.21.34.33`, AraCastPlayer 2.6.19). El player de oficinas `10.21.35.165` es producción: solo lectura salvo autorización expresa.

El protocolo de los players está en `channel-6/docs/llm/context/aracast-protocol.md`; el código de partida, en `channel-6/channel6-core` (`aracast/client`, `content/template`, `schedule/ics`, `publication`, `remote`, `capture`, `discovery`, `clock`, `network`).

## Diseño

- **Un tipo más de `Player`.** `players.kind` (`CEFA_TV` por defecto, `ARACAST`) y una tabla `aracast_players` (`player_id`, `host`, `mac`, `model`, `version`, contraseña SSH cifrada opcional). `device_token_hash` deja de ser obligatorio para los Aracast.
- **El manifest sigue siendo la fuente de verdad.** `ManifestService` calcula `desiredVersion` y la entrega igual que para cualquier player. Para un Aracast, un adaptador (empuje en lugar de descarga) traduce el manifest a plantillas y a `events.ics`, lo publica y marca la entrega en `DeliveryService` (`APPLIED` o `FAILED`) y `appliedVersion`. Si el player no responde, la entrega queda `QUEUED` y se reintenta cuando vuelve.
- **Resolución de prioridades en el servidor.** `events.ics` no tiene prioridades ni rangos de fechas; el adaptador resuelve por día con la misma regla que `architecture.md` y emite eventos sin solapes para una ventana móvil de días que se regenera cada noche.
- **Mismo protocolo hacia el navegador.** Vista previa, control remoto y terminal siguen usando `/ws/previews`, `/ws/remote/{id}` y `/ws/shell/{id}`; el core enruta por `kind` a la implementación de Aracast (captura `ffmpeg` por SSH, XTest, PTY SSH). La interfaz solo cambia en el alta, un distintivo y los ajustes propios (hora y red).
- **Contenidos.** Imagen → plantilla `Texture`; imágenes y PDF (renderizado a PNG) → `Slideshow`; vídeo → `Video`; web → `HTML5`. Una playlist homogénea es una plantilla; las mixtas se expanden como secuencias (ciclo de plantillas repetido en la franja), como hacía Channel 6. Los ficheros se nombran `<nombre>_<sha1>.<ext>`.
- **Credenciales por player.** Cada Aracast puede tener su contraseña SSH y FTP, guardadas cifradas con AES-256-GCM (`ARACAST_SECRET_KEY`, o el secreto de sesión si no se define). Al guardar una SSH se comprueba contra el player y, si no se indica FTP, se lee de `/root/.aracast/data/custom.cfg`. `ARACAST_SSH_PASSWORDS` y `ARACAST_FTP_PASSWORD` del `.env` quedan como respaldo para los que no tengan propia. Endpoints: `GET /players/aracast/{id}` y `PUT /players/aracast/{id}/credentials` (nunca devuelven las contraseñas).

## Fases

1. **Esquema y clientes.** Migración, `kind`, entidad `AracastPlayer`; port de los clientes de comandos (5401), FTP (5481) y SSH; alta por IP con sondeo; estado en línea cada minuto y datos (versión, MAC, resolución, plantilla actual, disco).
2. **Entrega.** Generador de plantillas, codec de `events.ics`, resolución de prioridades, cola con reintentos, historial en `deliveries`; playlists homogéneas.
3. **Órdenes y vista previa.** Reiniciar, reenviar programación, sincronizar la hora, cambio de red (solo administradores); vista previa y captura tras publicar.
4. **Control remoto y terminal.**
5. **Descubrimiento, interfaz y cierre.** Rangos CIDR en las sedes y botón de buscar Aracast; distintivo y diálogo de alta; secuencias para playlists mixtas; documentación (`architecture.md` en server y player); retirada de Channel 6.

## Estado

Terminado el 19 de octubre de 2026 y desplegado en `10.21.35.133`.

- Fases 0 y 1: esquema, clientes, alta por IP, estado en línea y credenciales por player (cifradas, con validación por SSH y lectura del FTP).
- Fase 2: planificador, publicador y trabajador de entregas con cola y reintentos. Probado contra `ARAPROD03`: plantillas, contenidos, `events.ics` y cambio de plantilla en emisión.
- Fase 3: reiniciar, hora (servidor o NTP), cambio de red (solo administradores) y vista previa por SSH.
- Fase 4: pantalla en vivo, teclado y ratón, y terminal SSH, tras los mismos WebSocket de los reproductores de CEFA TV mediante `AracastBridge`.
- Fase 5: rangos de IP en las sedes, descubrimiento, interfaz (distintivo, alta, ajustes, descubrimiento) y documentación. Playlists mixtas o con duraciones distintas, como secuencias de plantillas.
- Sobrescritura inmediata de una programación que ya se estaba emitiendo, siempre y sin opción.
- Channel 6 puede retirarse: sus funciones (inventario, descubrimiento, contenidos, programación, cola con reintentos, capturas, pantalla remota, terminal, hora y red) están en CEFA TV. Quedan fuera el cast de pantallas a salas de reuniones (mediamtx), el histórico propio de publicaciones (lo cubre el historial de entregas), el borrado manual de plantillas y la captura automática tras publicar.

## Límites conocidos

- Los ciclos de plantillas (playlists que mezclan tipos) solo se publican para las próximas tres horas y se regeneran cada 30 minutos; si el servidor dejara de publicar más de un par de horas, el player pasaría a la pantalla en reposo. Las playlists de un solo tipo se publican para siete días y se repiten cada semana.
- Los eventos de repetición corta (`FREQ=SECONDLY`) cuelgan al player, que calcula las repeticiones desde el año 2000: no se usan.
- Cada medianoche el player muestra 30 s la pantalla en reposo (`23:59:30`), como con la aplicación de escritorio.
- El banco de pruebas no tiene pantalla conectada, por lo que la pantalla en vivo y la vista previa no se han podido probar sobre un monitor real.
