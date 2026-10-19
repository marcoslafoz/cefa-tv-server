# CEFA TV Server

## Qué es

Emisor de CEFA TV, un sistema de señalización digital. Es una aplicación web donde se suben imágenes, vídeos, PDF, páginas web y HTML, se componen en playlists y se programan en el tiempo sobre las pantallas (reproductores) de la empresa.

## Por qué existe

Sustituye a herramientas como Xibo o Aracast con una solución propia, pensada para la infraestructura de CEFA: alta de pantallas con una clave en lugar de configurar direcciones IP, entrega fiable a pantallas que pierden la conexión y control remoto de la pantalla.

## Para quién

Administradores y equipos de contenido que programan qué se muestra en cada pantalla. Los usuarios se organizan en grupos de usuarios, que definen qué pueden hacer y sobre qué grupos de reproductores y sedes.

## Qué resuelve

- Biblioteca de medios con carpetas y almacenamiento en Garage (S3).
- Timeline visual con arrastrar y soltar para crear playlists y programar su emisión por pantalla, grupo, días y franjas horarias.
- Entrega diferida: si una pantalla está sin conexión, recibe la programación al volver.
- Emparejamiento de reproductores con un código que se muestra en pantalla y descubrimiento automático en la red.
- Control remoto (ratón, teclado y terminal) de cada pantalla, y vista previa de lo que emite.
- Sedes, grupos de reproductores y permisos por grupo de usuarios, con inicio de sesión local o con Microsoft.
- Auditoría de lo que hace cada usuario.

## Documentos relacionados

- [`architecture.md`](./architecture.md) — arquitectura, contrato de APIs y protocolo con los reproductores.
- [`reference-data.md`](./reference-data.md) — datos varios de referencia (URLs de entornos, sistemas externos, etc.).
