@echo off
rem SmartDesk dans Docker - double-clic depuis l'Explorateur
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0docker-demarrer.ps1"
echo.
pause
