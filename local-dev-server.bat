@echo off
setlocal

rem Serves the frontend through Vite with hot reloading. Requests to /api and /v3/api-docs are
rem proxied to the backend, so local-start.bat has to run in parallel for anything but static views.
rem SUMBOOKLM_BACKEND_URL overrides the proxy target.

cd /d "%~dp0sumbooklm-frontend"

set "PORT=5173"
if defined SUMBOOKLM_BACKEND_URL (set "BACKEND=%SUMBOOKLM_BACKEND_URL%") else (set "BACKEND=http://localhost:8080")

rem The Maven build installs a pinned Node toolchain below target. Preferring it keeps the dev
rem server on the same Node and npm versions the packaged build was produced with.
if exist "target\node\npm.cmd" set "PATH=%CD%\target\node;%PATH%"

where npm >nul 2>nul
if errorlevel 1 (
    echo npm is not on the PATH. Install Node, or run local-compile.bat once to fetch the pinned toolchain. 1>&2
    exit /b 1
)

if not exist "node_modules" (
    echo Installing frontend dependencies
    call npm ci
    if errorlevel 1 exit /b 1
)

echo Vite on http://localhost:%PORT%, proxying API requests to %BACKEND%
call npm run dev -- --port %PORT% --strictPort
exit /b %errorlevel%
