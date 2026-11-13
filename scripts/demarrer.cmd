@echo off
rem SmartDesk - lancement des 3 services (double-clic depuis l'Explorateur)
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
echo.
pause
