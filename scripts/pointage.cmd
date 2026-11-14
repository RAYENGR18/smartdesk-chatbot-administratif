@echo off
rem SmartDesk - pointage RFID des professeurs (lecteur Arduino + RC522 en USB)
rem Options : --port COM3 | --clavier | --test 04A13B2C
cd /d "%~dp0.."
set "PY=nlp\.venv\Scripts\python.exe"
if not exist "%PY%" set "PY=python"
"%PY%" -c "import serial" 2>nul || "%PY%" -m pip install -q pyserial
"%PY%" iot\pont_rfid.py %*
echo.
pause
