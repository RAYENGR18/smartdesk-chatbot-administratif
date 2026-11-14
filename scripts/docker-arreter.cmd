@echo off
rem SmartDesk dans Docker - arret des conteneurs (les donnees sont conservees)
cd /d "%~dp0.."
docker compose stop
echo.
echo SmartDesk est arrete. Pour le relancer : scripts\docker-demarrer.cmd
echo Pour tout effacer (base comprise) : docker compose down -v
echo.
pause
