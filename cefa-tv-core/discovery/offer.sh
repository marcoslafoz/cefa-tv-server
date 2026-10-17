#!/bin/sh
read -r request
case "$request" in
  CEFATV_DISCOVER*) ;;
  *) exit 0 ;;
esac

if [ -n "$PUBLIC_URL" ]; then
  api_url="$PUBLIC_URL"
else
  address=$(ip route get "$SOCAT_PEERADDR" 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p' | head -n 1)
  api_url="http://${address}:${API_PORT:-8080}"
fi

printf '{"service":"cefa-tv","version":1,"apiUrl":"%s"}\n' "$api_url"
