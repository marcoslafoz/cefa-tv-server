# CEFA TV App

Interfaz web de CEFA TV Server (Next.js 16, React 19, Tailwind 4, shadcn/ui, dnd-kit, pnpm). Habla con `cefa-tv-core` a través del prefijo `/core-api`.

## Pantallas

- Resumen: reproductores en línea, sin conexión y pendientes de entrega, medios y almacenamiento.
- Biblioteca: subida por arrastrar y soltar con progreso, miniaturas, filtros por tipo y alta de páginas web.
- Playlists: editor con timeline (arrastrar desde la biblioteca, reordenar, redimensionar la duración, propiedades y vista previa).
- Programación: carriles de 24 h por reproductor (vista de día) o de 7 días por reproductor (vista de semana); se arrastran playlists o medios sueltos al carril y los bloques se mueven y redimensionan con ajuste a 15 minutos.
- Reproductores: estado en vivo, historial de entregas, acciones y control remoto (ratón y teclado).
- Claves de alta, grupos y ajustes.

## Desarrollo

```bash
cp .env.template .env.local
pnpm install
pnpm dev
```

La aplicación escucha en `http://localhost:3000`. Necesita el core en `http://localhost:8080` (`CORE_API_URL`). El WebSocket del control remoto no pasa por los rewrites de Next.js: en desarrollo usa `NEXT_PUBLIC_CORE_WS_URL=ws://localhost:8080`; en producción nginx lo sirve en el mismo origen (`/core-api/ws/...`) y la variable se deja vacía.

## Comandos (`Makefile`)

`make help` lista los objetivos estándar (`build`, `up`, `down`, `rebuild`, `format`, `lint`, `test`, `check`, `clean`, `dev`, `logs`). No hay suite de pruebas todavía: `make test` solo lo indica.

## Docker

`make up` crea la red `cefa-tv-net` si no existe y levanta la aplicación y nginx (puerto `4000`). nginx proxifica `/core-api/` al contenedor `cefa-tv-core-app:8080` con soporte WebSocket y subidas de hasta 4 GB.

## Sesión

El core establece la cookie `ctv_session` (JWT, `httpOnly`, `Path=/`). El proxy de Next.js solo comprueba que exista para redirigir a `/login`; la validez la decide el core.
