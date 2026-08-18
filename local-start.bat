@echo off
setlocal

rem Starts the packaged application. Arguments are forwarded to Spring Boot, so a later
rem --server.port overrides the port set here.

cd /d "%~dp0"

set "ARTIFACT=sumbooklm-app\target\sumbooklm.jar"
set "PORT=8080"

where java >nul 2>nul
if errorlevel 1 (
    echo Java is not on the PATH. 1>&2
    exit /b 1
)

if not exist "%ARTIFACT%" (
    echo %ARTIFACT% does not exist. Run local-compile.bat first. 1>&2
    exit /b 1
)

echo Starting SumbookLM on http://localhost:%PORT%
java -jar "%ARTIFACT%" --server.port=%PORT% %*
exit /b %errorlevel%
