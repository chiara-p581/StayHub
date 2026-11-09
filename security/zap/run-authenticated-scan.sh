#!/usr/bin/env bash
set -euo pipefail

base_url="${STAYHUB_BASE_URL:-https://localhost:8443/StayHub}"
origin="${STAYHUB_ORIGIN:-https://localhost:8443}"
report_dir="${ZAP_REPORT_DIR:-$PWD/ci-evidence/zap}"
zap_image="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:stable}"

mkdir -p "$report_dir"
chmod 0777 "$report_dir"

temporary_files=()
cleanup() {
  if ((${#temporary_files[@]})); then rm -f "${temporary_files[@]}"; fi
}
trap cleanup EXIT

new_cookie_jar() {
  local jar
  jar="$(mktemp)"
  temporary_files+=("$jar")
  printf '%s' "$jar"
}

csrf_token() {
  local jar="$1"
  curl --insecure --fail --silent --show-error \
    --cookie "$jar" --cookie-jar "$jar" \
    "$base_url/api/seguridad/csrf" | jq -er '.token'
}

login() {
  local jar="$1" email="$2" password="$3" token
  token="$(csrf_token "$jar")"
  curl --insecure --fail --silent --show-error \
    --cookie "$jar" --cookie-jar "$jar" \
    --header "Origin: $origin" \
    --header "X-CSRF-Token: $token" \
    --header 'Content-Type: application/json' \
    --data "$(jq -cn --arg email "$email" --arg password "$password" '{email:$email,password:$password}')" \
    "$base_url/api/usuarios/login"
}

verify_admin_mfa() {
  local jar="$1" secret="$2" token code
  code="$(docker compose exec -T app java -cp /opt/jboss/ci-tools MfaTotpCode "$secret" | tr -d '\r\n')"
  test -n "$code"
  token="$(csrf_token "$jar")"
  curl --insecure --fail --silent --show-error \
    --cookie "$jar" --cookie-jar "$jar" \
    --header "Origin: $origin" \
    --header "X-CSRF-Token: $token" \
    --header 'Content-Type: application/json' \
    --data "$(jq -cn --arg codigo "$code" '{codigo:$codigo}')" \
    "$base_url/api/usuarios/mfa/verificar-login" >/dev/null
}

session_cookie() {
  local jar="$1"
  awk '$6 == "JSESSIONID" { value=$6 "=" $7 } END { print value }' "$jar"
}

run_zap() {
  local role="$1" target="$2" cookie="$3" exit_code=0
  echo "Ejecutando OWASP ZAP para el rol $role sobre $target"
  docker run --rm --network host \
    --volume "$report_dir:/zap/wrk:rw" \
    "$zap_image" zap-full-scan.py \
    -t "$target" -m 3 -T 10 -I \
    -J "$role.json" -r "$role.html" -w "$role.md" \
    -z "-config replacer.full_list(0).description=stayhub-$role -config replacer.full_list(0).enabled=true -config replacer.full_list(0).matchtype=REQ_HEADER -config replacer.full_list(0).matchstr=Cookie -config replacer.full_list(0).regex=false -config replacer.full_list(0).replacement=$cookie" \
    || exit_code=$?
  if ((exit_code == 3)); then
    echo "ZAP no pudo completar el escaneo de $role" >&2
    return 1
  fi
}

guest_jar="$(new_cookie_jar)"
login "$guest_jar" 'huesped@stayhub.local' 'StayHubDemo2026!' >/dev/null
guest_cookie="$(session_cookie "$guest_jar")"
test -n "$guest_cookie"

admin_jar="$(new_cookie_jar)"
admin_login="$(login "$admin_jar" 'admin@stayhub.local' 'StayHubDemo2026!')"
admin_secret="$(docker compose exec -T app cat /opt/jboss/local-security/demo-mfa-secret | tr -d '\r\n')"
test -n "$admin_secret"
if jq -e '.mfaRequerido == true' <<<"$admin_login" >/dev/null; then
  verify_admin_mfa "$admin_jar" "$admin_secret"
fi
admin_cookie="$(session_cookie "$admin_jar")"
test -n "$admin_cookie"

run_zap guest "$base_url/index.html" "$guest_cookie"
run_zap admin "$base_url/admin-dashboard.html" "$admin_cookie"

jq -s '{generatedAt:(now|todate), scans: map({site: .site})}' \
  "$report_dir/guest.json" "$report_dir/admin.json" > "$report_dir/combined.json"

high_count="$(jq '[.scans[].site[]?.alerts[]? | select(.riskcode == "3")] | length' "$report_dir/combined.json")"
medium_count="$(jq '[.scans[].site[]?.alerts[]? | select(.riskcode == "2")] | length' "$report_dir/combined.json")"

{
  echo '### OWASP ZAP autenticado'
  echo
  echo '| Rol | Entrada | Reportes |'
  echo '|---|---|---|'
  echo "| Huésped | \`/StayHub/index.html\` | \`guest.html\`, \`guest.json\`, \`guest.md\` |"
  echo "| Administrador + MFA | \`/StayHub/admin-dashboard.html\` | \`admin.html\`, \`admin.json\`, \`admin.md\` |"
  echo
  echo "Alertas High: **$high_count**. Alertas Medium: **$medium_count**."
} | tee "$report_dir/summary.md"

if ((high_count > 0)); then
  echo "Se detectaron $high_count alertas High de OWASP ZAP" >&2
  exit 1
fi
