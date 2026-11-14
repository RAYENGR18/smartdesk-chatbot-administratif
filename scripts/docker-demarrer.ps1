# =====================================================================
# SmartDesk dans Docker (Windows) - lance par scripts\docker-demarrer.cmd
#
#   1. verifie Docker Desktop (et le demarre si besoin) ;
#   2. cree le fichier .env avec des secrets aleatoires (s'il n'existe pas) ;
#   3. construit et demarre les conteneurs (docker compose up -d --build) ;
#   4. attend que le site reponde puis l'ouvre dans le navigateur.
# Journaux : docker.log et docker-build.log a la racine du projet.
# =====================================================================
$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
try { Start-Transcript -Path (Join-Path $Root "docker.log") -Force | Out-Null } catch { }

function Write-Step($m) { Write-Host ""; Write-Host "==> $m" -ForegroundColor Cyan }
function Write-Ok($m)   { Write-Host "    [OK] $m" -ForegroundColor Green }
function Write-Warn2($m) { Write-Host "    [!] $m" -ForegroundColor Yellow }
function Fail($m) {
    Write-Host ""
    Write-Host "[ERREUR] $m" -ForegroundColor Red
    try { Stop-Transcript | Out-Null } catch { }
    exit 1
}
function New-Secret([int]$bytes = 32) {
    $b = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    return ([Convert]::ToBase64String($b)).Replace('+', 'A').Replace('/', 'B').TrimEnd('=')
}
function Test-Docker {
    cmd /c "docker info >nul 2>&1"
    return ($LASTEXITCODE -eq 0)
}

# ---------------------------------------------------------------------
Write-Step "Docker"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail "Docker est introuvable. Installez Docker Desktop : https://www.docker.com/products/docker-desktop/ puis relancez ce script."
}
if (-not (Test-Docker)) {
    $exe = Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"
    if (Test-Path $exe) {
        Write-Host "    Demarrage de Docker Desktop (jusqu'a 3 minutes)..."
        Start-Process $exe | Out-Null
    } else {
        Write-Host "    Ouvrez Docker Desktop : ce script attend qu'il soit pret (3 minutes maximum)."
    }
    $deadline = (Get-Date).AddMinutes(3)
    while ((Get-Date) -lt $deadline -and -not (Test-Docker)) { Start-Sleep -Seconds 5 }
    if (-not (Test-Docker)) { Fail "Docker Desktop ne repond pas. Ouvrez-le, attendez 'Engine running', puis relancez ce script." }
}
cmd /c "docker compose version >nul 2>&1"
if ($LASTEXITCODE -ne 0) { Fail "'docker compose' est introuvable : mettez Docker Desktop a jour." }
Write-Ok ("Docker " + ((cmd /c "docker version --format {{.Server.Version}}" 2>$null) | Out-String).Trim())

# ---------------------------------------------------------------------
Write-Step "Fichier .env"
$envFile = Join-Path $Root ".env"
$example = Join-Path $Root ".env.example"
$adminGenere = $null
$utf8 = New-Object System.Text.UTF8Encoding($false)
if (Test-Path $envFile) {
    $existing = [System.IO.File]::ReadAllText($envFile)
    $added = @()
    foreach ($line in [System.IO.File]::ReadAllLines($example)) {
        if ($line -match '^([A-Z_]+)=(.*)$') {
            $key = $Matches[1]; $default = $Matches[2]
            if ($existing -notmatch "(?m)^$key=") {
                if (@("DB_PASSWORD", "JWT_SECRET", "NLP_API_KEY", "RFID_DEVICE_KEY") -contains $key) { $default = New-Secret 32 }
                if (-not $existing.EndsWith("`n")) { $existing += "`r`n" }
                $existing += "$key=$default`r`n"
                $added += $key
            }
        }
    }
    if ($added.Count -gt 0) {
        [System.IO.File]::WriteAllText($envFile, $existing, $utf8)
        Write-Ok (".env complete avec : " + ($added -join ", "))
    } else {
        Write-Ok ".env existe deja : conserve tel quel"
    }
} else {
    $adminGenere = New-Secret 9
    $c = [System.IO.File]::ReadAllText($example)
    $c = $c -replace '(?m)^DB_PASSWORD=[^\r\n]*', ("DB_PASSWORD=" + (New-Secret 24))
    $c = $c -replace '(?m)^JWT_SECRET=[^\r\n]*', ("JWT_SECRET=" + (New-Secret 48))
    $c = $c -replace '(?m)^NLP_API_KEY=[^\r\n]*', ("NLP_API_KEY=" + (New-Secret 32))
    $c = $c -replace '(?m)^RFID_DEVICE_KEY=[^\r\n]*', ("RFID_DEVICE_KEY=" + (New-Secret 32))
    $c = $c -replace '(?m)^ADMIN_PASSWORD=[^\r\n]*', ("ADMIN_PASSWORD=" + $adminGenere)
    [System.IO.File]::WriteAllText($envFile, $c, $utf8)
    Write-Ok ".env cree avec des secrets aleatoires"
}

$envValues = @{}
foreach ($line in [System.IO.File]::ReadAllLines($envFile)) {
    if ($line -match '^\s*([A-Z_]+)\s*=\s*(.*)$') { $envValues[$Matches[1]] = $Matches[2].Trim() }
}
$port = 8090
if ($envValues["APP_PORT"] -match '^\d+$') { $port = [int]$envValues["APP_PORT"] }
$url = "http://localhost:$port"

# Port deja pris par un autre programme ?
$conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
    $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
    $name = if ($proc) { $proc.ProcessName } else { "PID $($conn.OwningProcess)" }
    if ($name -notmatch 'docker|wslrelay|vpnkit|com\.docker') {
        Fail "Le port $port est deja utilise par $name. Choisissez un autre port dans .env (APP_PORT=8091 par exemple) puis relancez."
    }
}

# ---------------------------------------------------------------------
Write-Step "Construction et demarrage des conteneurs"
Write-Host "    La premiere fois : 10 a 20 minutes (telechargement des images et des dependances)."
Write-Host "    Les fois suivantes : quelques secondes. (journal : docker-build.log)"
cmd /c "docker compose up -d --build > docker-build.log 2>&1"
if ($LASTEXITCODE -ne 0) {
    Get-Content (Join-Path $Root "docker-build.log") -Tail 30 | ForEach-Object { Write-Host "    $_" }
    Fail "Le demarrage des conteneurs a echoue (detail : docker-build.log)."
}
Write-Ok "Conteneurs demarres"

# ---------------------------------------------------------------------
Write-Step "Attente du site ($url)"
$ok = $false
$deadline = (Get-Date).AddMinutes(5)
while ((Get-Date) -lt $deadline) {
    try {
        $r = Invoke-WebRequest -Uri "$url/api/health" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
    Start-Sleep -Seconds 5
}
cmd /c "docker compose ps"

Write-Host ""
if ($ok) {
    Write-Host "SmartDesk est accessible sur $url" -ForegroundColor Green
    Start-Process $url
} else {
    Write-Warn2 "Le site ne repond pas encore. Journaux : docker compose logs backend"
}
Write-Host ""
if ($adminGenere) {
    Write-Host "Compte administrateur : admin / $adminGenere   (aussi dans .env, ligne ADMIN_PASSWORD)" -ForegroundColor Green
} else {
    Write-Host "Compte administrateur : admin, mot de passe = ADMIN_PASSWORD dans .env"
}
if ($envValues["DEMO_DATA"] -ne "false") {
    Write-Host "Comptes de demo : prenom.nom (ex. yasmine.trabelsi), mot de passe $($envValues['DEMO_PASSWORD'])"
}
if (-not $envValues["GROQ_API_KEY"]) {
    Write-Warn2 "GROQ_API_KEY est vide : renseignez-la dans .env puis relancez ce script pour activer l'IA du chatbot."
}
Write-Host "Le chatbot est pret quelques minutes apres le premier demarrage (telechargement du modele)."
Write-Host "Arret : scripts\docker-arreter.cmd   -   Les donnees sont conservees entre deux demarrages."
try { Stop-Transcript | Out-Null } catch { }
