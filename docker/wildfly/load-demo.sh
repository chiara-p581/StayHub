#!/usr/bin/env bash
set -euo pipefail

base="https://localhost:8443/StayHub"
cookies="$(mktemp)"
respuesta="$(mktemp)"
trap 'rm -f "$cookies" "$respuesta"' EXIT

solicitar() {
  etiqueta="$1"
  shift
  estado="$(curl --silent --show-error --insecure --output "$respuesta" \
    --write-out '%{http_code}' "$@")"
  case "$estado" in
    2??) cat "$respuesta" ;;
    *)
      echo "$etiqueta falló con HTTP $estado:" >&2
      cat "$respuesta" >&2
      echo >&2
      return 1
      ;;
  esac
}

csrf() {
  solicitar "Obtención de CSRF" --cookie "$cookies" --cookie-jar "$cookies" \
    "$base/api/seguridad/csrf" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

token="$(csrf)"
test -n "$token"
solicitar "Login del administrador demo" --cookie "$cookies" --cookie-jar "$cookies" \
  --header "Origin: https://localhost:8443" \
  --header "X-CSRF-Token: $token" \
  --header "Content-Type: application/json" \
  --data '{"email":"admin@stayhub.local","password":"StayHubDemo2026!"}' \
  "$base/api/usuarios/login" >/dev/null

token="$(csrf)"
test -n "$token"
solicitar "Carga del catálogo demo" --cookie "$cookies" --cookie-jar "$cookies" \
  --header "Origin: https://localhost:8443" \
  --header "X-CSRF-Token: $token" \
  --header "Content-Type: application/json" \
  --data '{}' "$base/api/demo/cargar"
