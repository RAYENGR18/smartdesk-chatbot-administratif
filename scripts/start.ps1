# =====================================================================
# SmartDesk - lancement des 3 services en local (Windows)
#
# Usage : powershell -ExecutionPolicy Bypass -File scripts\start.ps1
#
# Ouvre une fenetre par service :
#   - NLP (FastAPI)     http://127.0.0.1:8000  (interne, appele par le backend)
#   - Backend (Spring)  http://localhost:8095  (SERVER_PORT dans .env)
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

# Ports lus dans .env
$envValues = @{}
Get-Content (Join-Path $Root ".env") -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.*)$') { $envValues[$Matches[1]] = $Matches[2].Trim() }
}
$backendPort = 8095
if ($envValues["SERVER_PORT"] -match '^\d+$') { $backendPort = [int]$envValues["SERVER_PORT"] }
$nlpPort = 8000
if ($envValues["NLP_URL"] -match ':(\d+)/?$') { $nlpPort = [int]$Matches[1] }
$frontPort = 61748

# Un port deja occupe empeche un service de demarrer : on le dit clairement
function Test-PortLibre([int]$port, [string]$name) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $conn) { return $true }
    $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
    $who = if ($proc) { "$($proc.ProcessName) (PID $($proc.Id))" } else { "PID $($conn.OwningProcess)" }
    Write-Host "    [!] Le port $port ($name) est deja utilise par $who." -ForegroundColor Yellow
    return $false
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

Write-Host "==> Verification des ports" -ForegroundColor Cyan
$nlpLibre = Test-PortLibre $nlpPort "service NLP"
$backendLibre = Test-PortLibre $backendPort "backend"
$frontLibre = Test-PortLibre $frontPort "frontend"
if (-not $backendLibre) {
    Write-Host "    Si c'est l'ancienne fenetre 'SmartDesk - Backend', fermez-la puis relancez scripts\demarrer.cmd." -ForegroundColor Yellow
    Write-Host "    Sinon, choisissez un autre port pour le backend dans .env (SERVER_PORT=8096 par exemple)." -ForegroundColor Yellow
    try { Stop-Transcript | Out-Null } catch { }
    exit 1
}
# Pour redemarrer seulement le backend : fermez sa fenetre et relancez demarrer.cmd
if (-not $nlpLibre) { Write-Host "    (le service NLP tourne deja : il ne sera pas relance)" -ForegroundColor Gray }
if (-not $frontLibre) { Write-Host "    (le frontend tourne deja : il ne sera pas relance)" -ForegroundColor Gray }

if ($nlpLibre) {
Write-Host "==> Demarrage du service NLP (le premier lancement telecharge le modele, ~500 Mo)" -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory (Join-Path $Root "nlp") -ArgumentList @(
    "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'SmartDesk - NLP'; & '$venvPython' main.py")
}

Write-Host "==> Demarrage du backend Spring Boot (port $backendPort)" -ForegroundColor Cyan
$javaSetup = ""
if ($JavaHome) { $javaSetup = "`$env:JAVA_HOME = '$JavaHome'; `$env:Path = '$JavaHome\bin;' + `$env:Path; " }
Start-Process powershell -WorkingDirectory (Join-Path $Root "backend") -ArgumentList @(
    "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'SmartDesk - Backend'; $javaSetup.\mvnw.cmd spring-boot:run")

if ($frontLibre) {
Write-Host "==> Demarrage du frontend Angular" -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory (Join-Path $Root "frontend") -ArgumentList @(
    "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'SmartDesk - Frontend'; `$env:NG_CLI_ANALYTICS = 'false'; `$env:SERVER_PORT = '$backendPort'; npm.cmd start")
}

Write-Host ""
Write-Host "==> Attente des services..." -ForegroundColor Cyan
$backendOk = Wait-Url "http://localhost:$backendPort/api/health" "Backend" 240
$frontOk = Wait-Url "http://localhost:$frontPort" "Frontend" 240
$nlpOk = Wait-Url "http://127.0.0.1:$nlpPort/health" "Service NLP" 300

Write-Host ""
if ($frontOk) {
    Write-Host "SmartDesk est accessible sur http://localhost:61748" -ForegroundColor Green
    Start-Process "http://localhost:61748"
}
Write-Host "Au premier demarrage, le mot de passe admin est celui de ADMIN_PASSWORD dans .env" -ForegroundColor Gray
try { Stop-Transcript | Out-Null } catch { }
