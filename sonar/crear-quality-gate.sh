#!/usr/bin/env bash
# Crea (o recrea) el Quality Gate "StayHub TP" con los criterios de la cátedra
# (Clase 15, sección 5) evaluados sobre TODO el código, no solo el código nuevo.
#
# Uso:
#   SONAR_TOKEN=... SONAR_HOST_URL=https://sonarcloud.io SONAR_ORGANIZATION=mi-org \
#     SONAR_PROJECT_KEY=mi-proyecto ./sonar/crear-quality-gate.sh
#
# En un SonarQube local no hace falta SONAR_ORGANIZATION.
set -euo pipefail

: "${SONAR_TOKEN:?Falta SONAR_TOKEN}"
HOST="${SONAR_HOST_URL:-https://sonarcloud.io}"
GATE="${SONAR_QUALITY_GATE:-StayHub TP}"
ORG_PARAM=()
[ -n "${SONAR_ORGANIZATION:-}" ] && ORG_PARAM=(--data-urlencode "organization=${SONAR_ORGANIZATION}")

api() { curl -sS -f -u "${SONAR_TOKEN}:" "$@"; }

# Si ya existía, se borra para que el script sea repetible
api -X POST "${HOST}/api/qualitygates/destroy" --data-urlencode "name=${GATE}" "${ORG_PARAM[@]}" >/dev/null 2>&1 || true
api -X POST "${HOST}/api/qualitygates/create"  --data-urlencode "name=${GATE}" "${ORG_PARAM[@]}" >/dev/null

condicion() { # métrica operador umbral
  api -X POST "${HOST}/api/qualitygates/create_condition" \
    --data-urlencode "gateName=${GATE}" --data-urlencode "metric=$1" \
    --data-urlencode "op=$2" --data-urlencode "error=$3" "${ORG_PARAM[@]}" >/dev/null
  echo "  condición: $1 $2 $3"
}

# SonarQube/SonarCloud siembran los gates nuevos con condiciones sobre "código nuevo"
# (new_violations, new_coverage, ...). La cátedra evalúa el código completo, así que se quitan.
api "${HOST}/api/qualitygates/show" --get --data-urlencode "name=${GATE}" "${ORG_PARAM[@]}"   | python3 -c "import sys,json; [print(c['id']) for c in json.load(sys.stdin)['conditions']]"   | while read -r id; do
      api -X POST "${HOST}/api/qualitygates/delete_condition" --data-urlencode "id=${id}" "${ORG_PARAM[@]}" >/dev/null
    done

echo "Quality Gate '${GATE}' en ${HOST}:"
# Rating A = 1. "Peor que A" (GT 1) falla. Rating A en confiabilidad implica 0 bugs (Blocker/Critical incluidos).
condicion reliability_rating          GT 1
condicion security_rating             GT 1
condicion vulnerabilities             GT 0
condicion duplicated_lines_density    GT 3
condicion security_hotspots_reviewed  LT 100

if [ -n "${SONAR_PROJECT_KEY:-}" ]; then
  api -X POST "${HOST}/api/qualitygates/select" --data-urlencode "gateName=${GATE}" \
    --data-urlencode "projectKey=${SONAR_PROJECT_KEY}" "${ORG_PARAM[@]}" >/dev/null
  echo "Asociado al proyecto ${SONAR_PROJECT_KEY}"
else
  echo "Sin SONAR_PROJECT_KEY: asociá el gate al proyecto a mano (Project Settings > Quality Gate)."
fi
