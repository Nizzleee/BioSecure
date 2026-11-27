@echo off
setlocal

set "SERVER_DIR=%~dp0"
set "CLOUDFLARED=%SERVER_DIR%bin\cloudflared.exe"

if not exist "%CLOUDFLARED%" (
  echo Descargando cloudflared...
  if not exist "%SERVER_DIR%bin" mkdir "%SERVER_DIR%bin"
  curl.exe -L --fail -o "%CLOUDFLARED%" https://github.com/cloudflare/cloudflared/releases/download/2026.11.2/cloudflared-windows-amd64.exe
)

start "BioSecure API" cmd /c ""%SERVER_DIR%start.cmd""
start "BioSecure Tunnel" "%CLOUDFLARED%" tunnel --no-autoupdate --url http://127.0.0.1:3001

echo.
echo Backend: http://127.0.0.1:3001/api/health
echo Tunnel: revisa la ventana "BioSecure Tunnel" y copia la URL https://xxxx.trycloudflare.com
echo Luego actualiza api-url.txt en el repo y haz push para que la app la use sin recompilar.
pause
