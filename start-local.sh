#!/usr/bin/env sh
set -eu

echo "Construyendo e iniciando StayHub, PostgreSQL y los mocks..."
docker compose up --build --detach --wait

echo "Cargando datos mínimos de demostración..."
docker compose exec -T app /opt/jboss/load-demo.sh

printf '\nStayHub está listo: https://localhost:8443/StayHub/index.html\n'
echo "Administrador: admin@stayhub.local / StayHubDemo2026!"
echo "Huésped:       huesped@stayhub.local / StayHubDemo2026!"
echo "El certificado HTTPS es local y autofirmado; el navegador puede pedir confirmación."
