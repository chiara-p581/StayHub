$ErrorActionPreference = "Stop"
Push-Location $PSScriptRoot
try {

Write-Host "Construyendo e iniciando StayHub, PostgreSQL y los mocks..."
docker compose up --build --detach --wait
if ($LASTEXITCODE -ne 0) { throw "No se pudo iniciar el entorno." }

Write-Host "Cargando datos mínimos de demostración..."
docker compose exec -T app /opt/jboss/load-demo.sh
if ($LASTEXITCODE -ne 0) { throw "La aplicación inició, pero no se pudieron cargar los datos demo." }

& "$PSScriptRoot\trust-local-certificate.cmd"
if ($LASTEXITCODE -ne 0) {
    Write-Warning "StayHub funciona, pero no se pudo configurar la confianza del certificado."
}

Write-Host ""
Write-Host "StayHub está listo: https://localhost:8443/StayHub/index.html"
Write-Host "Administrador: admin@stayhub.local / StayHubDemo2026!"
Write-Host "Huésped:       huesped@stayhub.local / StayHubDemo2026!"
Write-Host "El certificado HTTPS es local y autofirmado; el navegador puede pedir confirmación."
} finally {
    Pop-Location
}
