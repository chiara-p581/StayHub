@echo off
setlocal
pushd "%~dp0"
docker compose down
set "resultado=%errorlevel%"
popd
exit /b %resultado%
