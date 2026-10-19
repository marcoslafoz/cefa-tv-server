#!/bin/sh
set -eu

: "${GARAGE_RPC_SECRET:?falta GARAGE_RPC_SECRET}"
: "${S3_ACCESS_KEY:?falta S3_ACCESS_KEY}"
: "${S3_SECRET_KEY:?falta S3_SECRET_KEY}"
: "${S3_BUCKET:?falta S3_BUCKET}"
CAPACITY="${GARAGE_CAPACITY:-100G}"

log() { printf '[garage-init] %s\n' "$*"; }

for _ in $(seq 1 60); do
  garage status >/dev/null 2>&1 && break
  sleep 2
done
garage status >/dev/null 2>&1 || { log "Garage no responde"; exit 1; }

if garage status | grep -q "NO ROLE ASSIGNED"; then
  NODE_ID="$(garage status | awk '/NO ROLE ASSIGNED/ {print $1; exit}')"
  log "Asignando el layout al nodo $NODE_ID"
  garage layout assign -z dc1 -c "$CAPACITY" "$NODE_ID"
  garage layout apply --version 1
fi

if ! garage key info "$S3_ACCESS_KEY" >/dev/null 2>&1; then
  log "Importando la clave de acceso"
  garage key import --yes -n cefa-tv-core "$S3_ACCESS_KEY" "$S3_SECRET_KEY"
fi

if ! garage bucket info "$S3_BUCKET" >/dev/null 2>&1; then
  log "Creando el bucket $S3_BUCKET"
  garage bucket create "$S3_BUCKET"
fi
garage bucket allow --read --write --owner "$S3_BUCKET" --key "$S3_ACCESS_KEY"
log "Almacenamiento listo"
