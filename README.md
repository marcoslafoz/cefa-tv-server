# CEFA TV Server

Emisor de CEFA TV, el sistema de señalización digital de CEFA. Permite subir imágenes, vídeos, PDF, páginas web y HTML, componerlos en playlists con un timeline visual y programarlos sobre las pantallas (reproductores). Los reproductores se instalan desde [`cefa-tv-player`](../cefa-tv-player).

## Estructura del repositorio

- `cefa-tv-core/` — API backend (Spring Boot 3.4, Java 21, PostgreSQL, Garage). Incluye el sidecar de descubrimiento en red local.
- `cefa-tv-app/` — interfaz web (Next.js 16, React 19, pnpm, Tailwind CSS).
- `docs/llm/` — contexto y reglas para trabajar en el proyecto, incluida la [arquitectura y el contrato con los reproductores](docs/llm/context/architecture.md).

## Arranque rápido

```bash
cp cefa-tv-core/.env.template cefa-tv-core/.env
cp cefa-tv-app/.env.template cefa-tv-app/.env
make up
```

La interfaz queda en `http://<servidor>:4000`. Los reproductores se conectan a `http://<servidor>:8080`. El primer administrador se crea con `ADMIN_USERNAME` y `ADMIN_PASSWORD` de `cefa-tv-core/.env`.

Para dar de alta una pantalla: ejecuta en la máquina del reproductor `./install.sh`; mostrará un código `ABC-123` en la pantalla. Introdúcelo en **Reproductores, Añadir reproductor**.

## Desarrollo local (`Makefile`)

Cada proyecto tiene su `Makefile` y la raíz los orquesta. Comandos: `make help`, `build`, `up`, `down`, `rebuild`, `format`, `lint`, `test`, `check`, `clean`, `prune` (solo raíz). Convenciones en [`makefile.md`](docs/llm/rules/common/makefile.md).

## Documentación

El punto de entrada es [`CLAUDE.md`](./CLAUDE.md).
