@echo off
setlocal
pushd "%~dp0"

echo Construyendo e iniciando StayHub, PostgreSQL y los mocks...
docker compose up --build --detach --wait
if errorlevel 1 goto :error_start

echo Cargando datos minimos de demostracion...
docker compose exec -T app /opt/jboss/load-demo.sh
if errorlevel 1 goto :error_demo

call "%~dp0trust-local-certificate.cmd"
if errorlevel 1 (
  echo Aviso: StayHub funciona, pero no se pudo configurar la confianza del certificado.
  echo Puedes ejecutar trust-local-certificate.cmd mas tarde.
)

echo.
echo StayHub esta listo: https://localhost:8443/StayHub/index.html
echo Administrador: admin@stayhub.local / StayHubDemo2026!
echo Huesped:       huesped@stayhub.local / StayHubDemo2026!
echo El certificado HTTPS es local y autofirmado; el navegador puede pedir confirmacion.
popd
exit /b 0

:error_start
echo No se pudo iniciar el entorno.
popd
exit /b 1

:error_demo
echo La aplicacion inicio, pero no se pudieron cargar los datos demo.
popd
exit /b 1
