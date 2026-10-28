@echo off
setlocal
pushd "%~dp0"

if not exist ".local-demo" mkdir ".local-demo"
set "certificado=%CD%\.local-demo\stayhub-local.cer"
set "certificadoConfiado=%CD%\.local-demo\stayhub-local-trusted.cer"

echo Exportando el certificado HTTPS del contenedor local...
docker compose exec -T app keytool -exportcert -rfc ^
  -alias stayhub-local ^
  -keystore /opt/jboss/wildfly/standalone/configuration/stayhub-local.p12 ^
  -storepass stayhub-local > "%certificado%"
if errorlevel 1 goto :error_exportar

certutil -dump "%certificado%" >nul
if errorlevel 1 goto :error_certificado

if exist "%certificadoConfiado%" (
  fc /b "%certificado%" "%certificadoConfiado%" >nul 2>&1
  if not errorlevel 1 (
    echo El certificado local ya fue configurado anteriormente.
    popd
    exit /b 0
  )
)

echo.
echo Se agregara SOLO el certificado local de StayHub al almacen de confianza de Windows.
echo Archivo: %certificado%
choice /C SN /N /M "Continuar? [S/N]: "
if errorlevel 2 goto :cancelado

certutil -user -addstore -f Root "%certificado%"
if errorlevel 1 goto :error_importar
copy /y "%certificado%" "%certificadoConfiado%" >nul

echo.
echo Certificado agregado correctamente.
echo Cierra por completo Firefox, vuelve a abrirlo y entra a:
echo https://localhost:8443/StayHub/index.html
popd
exit /b 0

:error_exportar
echo No se pudo exportar el certificado. Verifica que StayHub este iniciado.
popd
exit /b 1

:error_certificado
echo El archivo exportado no contiene un certificado valido.
popd
exit /b 1

:error_importar
echo Windows no pudo agregar el certificado al almacen de confianza.
popd
exit /b 1

:cancelado
echo Operacion cancelada. No se modifico el almacen de certificados.
popd
exit /b 0
