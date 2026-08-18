@echo off
setlocal

rem Builds every module and packages the executable artifact.
rem Arguments are forwarded to Maven, so -Dfrontend.skip=true builds the backend only.

cd /d "%~dp0"

set "REQUIRED_JAVA_MAJOR=25"
set "ARTIFACT=sumbooklm-app\target\sumbooklm.jar"

where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven is not on the PATH. This project carries no Maven wrapper, so mvn has to be installed. 1>&2
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo Java is not on the PATH. JDK %REQUIRED_JAVA_MAJOR% or newer is required. 1>&2
    exit /b 1
)

set "JAVA_VERSION="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VERSION=%%~v"
set "JAVA_MAJOR="
for /f "tokens=1 delims=.+-_" %%v in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%v"
if defined JAVA_MAJOR (
    echo %JAVA_MAJOR%|findstr /r "^[0-9][0-9]*$" >nul
    if not errorlevel 1 if %JAVA_MAJOR% LSS %REQUIRED_JAVA_MAJOR% (
        echo Java %JAVA_VERSION% found, but JDK %REQUIRED_JAVA_MAJOR% or newer is required. 1>&2
        exit /b 1
    )
)

call mvn clean install %*
if errorlevel 1 exit /b %errorlevel%

echo.
echo Artifact: %ARTIFACT%
echo Run local-start.bat to start it, or local-dev-server.bat for the frontend dev server.
exit /b 0
