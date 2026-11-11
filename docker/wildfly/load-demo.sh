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
login="$(solicitar "Login del administrador demo" --cookie "$cookies" --cookie-jar "$cookies" \
  --header "Origin: https://localhost:8443" \
  --header "X-CSRF-Token: $token" \
  --header "Content-Type: application/json" \
  --data '{"email":"admin@stayhub.local","password":"StayHubDemo2026!"}' \
  "$base/api/usuarios/login")"

secret_file="/opt/jboss/local-security/demo-mfa-secret"
if grep -q '"mfaConfigurado":false' <<<"$login"; then
  token="$(csrf)"
  preparacion="$(solicitar "Preparación del MFA demo" --cookie "$cookies" --cookie-jar "$cookies" \
    --header "Origin: https://localhost:8443" \
    --header "X-CSRF-Token: $token" \
    --header "Content-Type: application/json" \
    --data '{}' "$base/api/usuarios/mfa/preparar")"
  secret="$(sed -n 's/.*"secreto":"\([^"]*\)".*/\1/p' <<<"$preparacion")"
  test -n "$secret"
  printf '%s' "$secret" > "$secret_file"
  chmod 600 "$secret_file"
  code="$(java -cp /opt/jboss/ci-tools MfaTotpCode "$secret")"
  token="$(csrf)"
  solicitar "Activación del MFA demo" --cookie "$cookies" --cookie-jar "$cookies" \
    --header "Origin: https://localhost:8443" \
    --header "X-CSRF-Token: $token" \
    --header "Content-Type: application/json" \
    --data "{\"codigo\":\"$code\"}" "$base/api/usuarios/mfa/activar" >/dev/null
else
  test -s "$secret_file" || { echo "Falta el secreto MFA del administrador demo" >&2; exit 1; }
  secret="$(cat "$secret_file")"
  code="$(java -cp /opt/jboss/ci-tools MfaTotpCode "$secret")"
  token="$(csrf)"
  solicitar "Verificación del MFA demo" --cookie "$cookies" --cookie-jar "$cookies" \
    --header "Origin: https://localhost:8443" \
    --header "X-CSRF-Token: $token" \
    --header "Content-Type: application/json" \
    --data "{\"codigo\":\"$code\"}" "$base/api/usuarios/mfa/verificar-login" >/dev/null
fi

token="$(csrf)"
test -n "$token"
solicitar "Carga del catálogo demo" --cookie "$cookies" --cookie-jar "$cookies" \
  --header "Origin: https://localhost:8443" \
  --header "X-CSRF-Token: $token" \
  --header "Content-Type: application/json" \
  --data '{}' "$base/api/demo/cargar"
