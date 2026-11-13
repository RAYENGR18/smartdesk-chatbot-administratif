@echo off
rem SmartDesk - installation puis lancement (double-clic depuis l'Explorateur)
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"
if errorlevel 1 (
  echo.
  echo L'installation a echoue : le detail est dans le fichier setup.log
  echo.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
echo.
pause
