# =====================================================================
# SmartDesk - lancement des 3 services en local (Windows)
#
# Usage : powershell -ExecutionPolicy Bypass -File scripts\start.ps1
#
# Ouvre une fenetre par service :
#   - NLP (FastAPI)     http://127.0.0.1:8000  (interne, appele par le backend)
#   - Backend (Spring)  http://localhost:8080
#   - Frontend          http://localhost:61748
# Pour tout arreter : fermez les 3 fenetres (ou Ctrl+C dans chacune).
# =====================================================================
$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $PSScriptRoot
try { Start-Transcript -Path (Join-Path $Root "start.log") -Force | Out-Null } catch { }

if (-not (Test-Path (Join-Path $Root ".env"))) {
    Write-Host "[ERREUR] Fichier .env absent : lancez d'abord scripts\setup.ps1" -ForegroundColor Red
    exit 1
}

$JavaHome = $env:JAVA_HOME
$localConfig = Join-Path $PSScriptRoot "local-config.ps1"
if (Test-Path $localConfig) { . $localConfig }

$venvPython = Join-Path $Root "nlp\.venv\Scripts\python.exe"
if (-not (Test-Path $venvPython)) {
    Write-Host "[ERREUR] Environnement Python absent : lancez d'abord scripts\setup.ps1" -ForegroundColor Red
    exit 1
}

function Wait-Url([string]$url, [string]$name, [int]$timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) { Write-Host "    [OK] $name pret" -ForegroundColor Green; return $true }
        } catch { }
        Start-Sleep -Seconds 3
    }
    Write-Host "    [!] $name ne repond pas encore : regardez sa fenetre" -ForegroundColor Yellow
    return $false
}

Write-Host "==> Demarrage du service NLP (le premier lancement telecharge le modele, ~500 Mo)" -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory (Join-Path $Root "nlp") -ArgumentList @(
    "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'SmartDesk - NLP'; & '$venvPython' main.py")

Write-Host "==> Demarrage du backend Spring Boot" -ForegroundColor Cyan
$javaSetup = ""
if ($JavaHome) { $javaSetup = "`$env:JAVA_HOME = '$JavaHome'; `$env:Path = '$JavaHome\bin;' + `$env:Path; " }
Start-Process powershell -WorkingDirectory (Join-Path $Root "backend") -ArgumentList @(
    "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'SmartDesk - Backend'; $javaSetup.\mvnw.cmd spring-boot:run")

Write-Host "==> Demarrage du frontend Angular" -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory (Join-Path $Root "frontend") -ArgumentList @(
    "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'SmartDesk - Frontend'; `$env:NG_CLI_ANALYTICS = 'false'; npm start")

Write-Host ""
Write-Host "==> Attente des services..." -ForegroundColor Cyan
$backendOk = Wait-Url "http://localhost:8080/api/health" "Backend" 240
$frontOk = Wait-Url "http://localhost:61748" "Frontend" 240
$nlpOk = Wait-Url "http://127.0.0.1:8000/health" "Service NLP" 300

Write-Host ""
if ($frontOk) {
    Write-Host "SmartDesk est accessible sur http://localhost:61748" -ForegroundColor Green
    Start-Process "http://localhost:61748"
}
Write-Host "Au premier demarrage, le mot de passe admin est celui de ADMIN_PASSWORD dans .env" -ForegroundColor Gray
try { Stop-Transcript | Out-Null } catch { }
