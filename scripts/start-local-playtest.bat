@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "POWERSHELL_SCRIPT=%SCRIPT_DIR%start-local-playtest.ps1"

if not exist "%POWERSHELL_SCRIPT%" (
  echo Missing script: "%POWERSHELL_SCRIPT%"
  pause
  exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%POWERSHELL_SCRIPT%"
if errorlevel 1 (
  echo.
  echo Local playtest startup failed.
  pause
  exit /b 1
)

exit /b 0
