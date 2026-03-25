@echo off
setlocal
set APP_HOME=%~dp0
set GRADLE_VERSION=6.7.1
set DIST_NAME=gradle-%GRADLE_VERSION%
set DIST_ZIP=%APP_HOME%\.gradle-bootstrap\%DIST_NAME%-bin.zip
set DIST_DIR=%APP_HOME%\.gradle-bootstrap\%DIST_NAME%
if not exist "%DIST_DIR%\bin\gradle.bat" (
  if not exist "%APP_HOME%\.gradle-bootstrap" mkdir "%APP_HOME%\.gradle-bootstrap"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/%DIST_NAME%-bin.zip' -OutFile '%DIST_ZIP%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%DIST_ZIP%' '%APP_HOME%\.gradle-bootstrap'"
)
call "%DIST_DIR%\bin\gradle.bat" %*

