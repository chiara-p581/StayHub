$ErrorActionPreference = "Stop"
docker compose down
if ($LASTEXITCODE -ne 0) { throw "No se pudo detener el entorno." }
