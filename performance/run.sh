#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
repo_dir="$(dirname "$script_dir")"
mkdir -p "$script_dir/reports"

if command -v k6 >/dev/null 2>&1; then
  cd "$repo_dir"
  exec k6 run performance/k6-critical-endpoints.js
fi

if command -v docker >/dev/null 2>&1; then
  base_url="${BASE_URL:-https://host.docker.internal:8443/StayHub/api}"
  exec docker run --rm \
    --add-host=host.docker.internal:host-gateway \
    --volume "$repo_dir:/work" \
    --workdir /work \
    --env "BASE_URL=$base_url" \
    --env "VUS=${VUS:-50}" \
    --env "DURATION=${DURATION:-5m}" \
    grafana/k6:2.3.0 run performance/k6-critical-endpoints.js
fi

echo "Se necesita k6 o Docker para ejecutar la prueba de carga." >&2
exit 127
