# =====================================================================
# SmartDesk - installation locale sous Windows
#
# Usage (depuis le dossier du projet) :
#   powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
#
# Ce script :
#   1. verifie Java 17+, Node.js, Python 3.11-3.13 et PostgreSQL ;
#   2. cree le fichier .env avec des secrets aleatoires ;
#   3. cree l'utilisateur et la base PostgreSQL ;
#   4. installe les dependances (Python, npm, Maven).
# Il peut etre relance sans risque : rien n'est ecrase.
# =====================================================================
param(
    [string]$PgBin = "",
    [string]$PgHost = "localhost",
    [int]$PgPort = 5432,
    [string]$PgAdmin = "postgres",
    [switch]$SkipDatabase,
    [switch]$SkipDependencies
)

# "Continue" : sous Windows PowerShell 5.1, "Stop" interrompt le script des qu'un
# programme (java -version, psql...) ecrit sur stderr. Les erreurs sont testees a la main.
$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

# Journal de l'installation (les mots de passe saisis n'y apparaissent pas)
try { Start-Transcript -Path (Join-Path $Root "setup.log") -Force | Out-Null } catch { }

function Write-Step([string]$msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg) { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host "    [!] $msg" -ForegroundColor Yellow }
function Fail([string]$msg) {
    Write-Host ""; Write-Host "[ERREUR] $msg" -ForegroundColor Red
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    try { Stop-Transcript | Out-Null } catch { }
    exit 1
}

function New-Secret([int]$bytes = 48) {
    $b = New-Object byte[] $bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($b)
    return ([Convert]::ToBase64String($b)).Replace('+', '-').Replace('/', '_').TrimEnd('=')
}

function Get-JavaMajor([string]$javaExe) {
    try {
        $out = & $javaExe -version 2>&1 | Out-String
        if ($out -match 'version "(\d+)(\.(\d+))?') {
            $major = [int]$Matches[1]
            if ($major -eq 1 -and $Matches[3]) { $major = [int]$Matches[3] }
            return $major
        }
    } catch { }
    return 0
}

# ---------------------------------------------------------------------
Write-Step "Verification des prerequis"

# Java 17+ : PATH, JAVA_HOME, puis JDK installes (IntelliJ, Adoptium, Microsoft, Oracle)
$JavaHome = $null
$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if ($javaCmd) { $candidates += (Split-Path -Parent (Split-Path -Parent $javaCmd.Source)) }
foreach ($dir in @("$env:USERPROFILE\.jdks", "C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium",
                   "C:\Program Files\Microsoft", "C:\Program Files\Zulu", "C:\Program Files\Amazon Corretto")) {
    if (Test-Path $dir) {
        Get-ChildItem $dir -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending |
            ForEach-Object { $candidates += $_.FullName }
    }
}
foreach ($c in $candidates) {
    $exe = Join-Path $c "bin\java.exe"
    if ((Test-Path $exe) -and ((Get-JavaMajor $exe) -ge 17)) { $JavaHome = $c; break }
}
if (-not $JavaHome) { Fail "Java 17 ou plus est introuvable. Installez par exemple Temurin 21 : https://adoptium.net" }
Write-Ok "Java $(Get-JavaMajor (Join-Path $JavaHome 'bin\java.exe')) : $JavaHome"

# Node.js 20.19+ / 22.12+ (exige par Angular 21)
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) { Fail "Node.js est introuvable. Installez la version LTS : https://nodejs.org" }
$nodeVersion = (& node -v).TrimStart('v')
$nv = $nodeVersion.Split('.')
if ([int]$nv[0] -lt 20 -or ([int]$nv[0] -eq 20 -and [int]$nv[1] -lt 19) -or ([int]$nv[0] -eq 22 -and [int]$nv[1] -lt 12)) {
    Fail "Node.js $nodeVersion est trop ancien (minimum 20.19 ou 22.12). Installez la version LTS : https://nodejs.org"
}
Write-Ok "Node.js $nodeVersion"

# Python 3.11 a 3.13 (launcher py en priorite)
$PythonExe = $null
$py = Get-Command py -ErrorAction SilentlyContinue
if ($py) {
    foreach ($v in @("3.12", "3.11", "3.13")) {
        try {
            $exe = (& py "-$v" -c "import sys; print(sys.executable)" 2>$null)
            if ($LASTEXITCODE -eq 0 -and $exe) { $PythonExe = $exe.Trim(); break }
        } catch { }
    }
}
if (-not $PythonExe) {
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) {
        $ver = (& python -c "import sys; print('%d.%d' % sys.version_info[:2])" 2>$null)
        if ($ver -in @("3.11", "3.12", "3.13")) { $PythonExe = (& python -c "import sys; print(sys.executable)").Trim() }
    }
}
if (-not $PythonExe) { Fail "Python 3.11, 3.12 ou 3.13 est introuvable. Installez Python 3.12 : https://www.python.org/downloads/ (cochez 'Add to PATH')" }
Write-Ok "Python : $PythonExe"

# PostgreSQL (psql.exe)
$Psql = $null
if ($PgBin) { $Psql = Join-Path $PgBin "psql.exe" }
else {
    $psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($psqlCmd) { $Psql = $psqlCmd.Source }
    else {
        foreach ($v in @("18", "17", "16", "15")) {
            $p = "C:\Program Files\PostgreSQL\$v\bin\psql.exe"
            if (Test-Path $p) { $Psql = $p; break }
        }
    }
}
if (-not $SkipDatabase) {
    if (-not $Psql -or -not (Test-Path $Psql)) {
        Fail "psql.exe introuvable. Indiquez le dossier : -PgBin 'C:\Program Files\PostgreSQL\18\bin'"
    }
    Write-Ok "PostgreSQL : $Psql"
}

# Chemins memorises pour scripts\start.ps1
$localConfig = Join-Path $PSScriptRoot "local-config.ps1"
$cfg = "# Genere par setup.ps1 - ne pas commiter`r`n`$JavaHome = '$JavaHome'`r`n`$PythonExe = '$PythonExe'`r`n"
[System.IO.File]::WriteAllText($localConfig, $cfg, (New-Object System.Text.UTF8Encoding($false)))

# ---------------------------------------------------------------------
Write-Step "Fichier .env"

$envFile = Join-Path $Root ".env"
$generatedAdminPassword = $null
if (Test-Path $envFile) {
    Write-Ok ".env existe deja : conserve tel quel"
} else {
    $content = Get-Content (Join-Path $Root ".env.example") -Raw -Encoding UTF8
    $generatedAdminPassword = New-Secret 12
    $content = $content -replace '(?m)^DB_PASSWORD=[^\r\n]*', ("DB_PASSWORD=" + (New-Secret 24))
    $content = $content -replace '(?m)^JWT_SECRET=[^\r\n]*', ("JWT_SECRET=" + (New-Secret 48))
    $content = $content -replace '(?m)^NLP_API_KEY=[^\r\n]*', ("NLP_API_KEY=" + (New-Secret 32))
    $content = $content -replace '(?m)^ADMIN_PASSWORD=[^\r\n]*', ("ADMIN_PASSWORD=" + $generatedAdminPassword)

    Write-Host "    Cle Groq (https://console.groq.com/keys) - collez-la puis Entree," -ForegroundColor Gray
    Write-Host "    ou laissez vide pour la renseigner plus tard dans .env :" -ForegroundColor Gray
    $groqSecure = Read-Host "    GROQ_API_KEY" -AsSecureString
    $gbstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($groqSecure)
    $groq = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($gbstr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($gbstr)
    if ($groq) {
        $groq = $groq.Trim()
        $content = $content -replace '(?m)^GROQ_API_KEY=[^\r\n]*', ("GROQ_API_KEY=" + $groq)
    }
    [System.IO.File]::WriteAllText($envFile, $content, (New-Object System.Text.UTF8Encoding($false)))
    Write-Ok ".env cree avec des secrets aleatoires"
}

# Lecture des valeurs du .env
$envValues = @{}
Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.*)$') { $envValues[$Matches[1]] = $Matches[2].Trim() }
}

# ---------------------------------------------------------------------
if (-not $SkipDatabase) {
    Write-Step "Base de donnees PostgreSQL"

    $dbUser = $envValues["DB_USERNAME"]
    $dbPassword = $envValues["DB_PASSWORD"]
    $dbName = "chatbot_db"
    if ($envValues["DB_URL"] -match '/([A-Za-z0-9_]+)(\?.*)?$') { $dbName = $Matches[1] }

    if ($dbUser -notmatch '^[A-Za-z_][A-Za-z0-9_]*$' -or $dbName -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        Fail "DB_USERNAME et le nom de base ne doivent contenir que lettres, chiffres et '_'"
    }
    if (-not $dbPassword -or $dbPassword.Contains("'")) { Fail "DB_PASSWORD vide ou invalide dans .env" }

    Write-Host "    Mot de passe de l'utilisateur '$PgAdmin' choisi a l'installation de PostgreSQL :" -ForegroundColor Gray
    $secure = Read-Host "    Mot de passe $PgAdmin" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

    try {
        $psqlArgs = @("-h", $PgHost, "-p", "$PgPort", "-U", $PgAdmin, "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-tAq")

        $roleExists = (& $Psql @psqlArgs -c "SELECT 1 FROM pg_roles WHERE rolname = '$dbUser'")
        if ($LASTEXITCODE -ne 0) { Fail "Connexion a PostgreSQL impossible (mot de passe '$PgAdmin' incorrect ou serveur arrete)." }
        if ("$roleExists".Trim() -eq "1") {
            & $Psql @psqlArgs -c "ALTER ROLE $dbUser WITH LOGIN PASSWORD '$dbPassword'" | Out-Null
            Write-Ok "Utilisateur '$dbUser' existant : mot de passe synchronise avec .env"
        } else {
            & $Psql @psqlArgs -c "CREATE ROLE $dbUser WITH LOGIN PASSWORD '$dbPassword'" | Out-Null
            Write-Ok "Utilisateur '$dbUser' cree"
        }
        if ($LASTEXITCODE -ne 0) { Fail "Creation de l'utilisateur PostgreSQL impossible." }

        $dbExists = (& $Psql @psqlArgs -c "SELECT 1 FROM pg_database WHERE datname = '$dbName'")
        if ("$dbExists".Trim() -eq "1") {
            & $Psql @psqlArgs -c "ALTER DATABASE $dbName OWNER TO $dbUser" | Out-Null
            Write-Ok "Base '$dbName' existante : proprietaire = $dbUser"
        } else {
            & $Psql @psqlArgs -c "CREATE DATABASE $dbName OWNER $dbUser ENCODING 'UTF8' TEMPLATE template0" | Out-Null
            if ($LASTEXITCODE -ne 0) { & $Psql @psqlArgs -c "CREATE DATABASE $dbName OWNER $dbUser" | Out-Null }
            Write-Ok "Base '$dbName' creee"
        }
        if ($LASTEXITCODE -ne 0) { Fail "Creation de la base impossible." }

        # L'utilisateur applicatif doit pouvoir creer les tables dans le schema public
        $schemaArgs = @("-h", $PgHost, "-p", "$PgPort", "-U", $PgAdmin, "-d", $dbName, "-v", "ON_ERROR_STOP=1", "-tAq")
        & $Psql @schemaArgs -c "ALTER SCHEMA public OWNER TO $dbUser" | Out-Null
        Write-Ok "Schema public attribue a $dbUser"
    } finally {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
}

# ---------------------------------------------------------------------
if (-not $SkipDependencies) {
    Write-Step "Service NLP : environnement Python (quelques minutes la premiere fois)"
    $venv = Join-Path $Root "nlp\.venv"
    if (-not (Test-Path (Join-Path $venv "Scripts\python.exe"))) {
        & $PythonExe -m venv $venv
        if ($LASTEXITCODE -ne 0) { Fail "Creation de l'environnement Python impossible." }
    }
    $venvPython = Join-Path $venv "Scripts\python.exe"
    & $venvPython -m pip install --upgrade pip --quiet
    & $venvPython -m pip install -r (Join-Path $Root "nlp\requirements.txt")
    if ($LASTEXITCODE -ne 0) { Fail "Installation des dependances Python impossible." }
    & $venvPython -m spacy download fr_core_news_sm
    if ($LASTEXITCODE -ne 0) { Write-Warn "Modele spaCy non installe : le service fonctionnera sans extraction d'entites." }
    Write-Ok "Dependances Python installees"

    Write-Step "Frontend : dependances npm"
    Push-Location (Join-Path $Root "frontend")
    try {
        & npm ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { Fail "npm ci a echoue." }
    } finally { Pop-Location }
    Write-Ok "Dependances npm installees"

    Write-Step "Backend : compilation Maven"
    $env:JAVA_HOME = $JavaHome
    Push-Location (Join-Path $Root "backend")
    try {
        & .\mvnw.cmd -q -B -DskipTests package
        if ($LASTEXITCODE -ne 0) { Fail "La compilation du backend a echoue." }
    } finally { Pop-Location }
    Write-Ok "Backend compile"
}

# ---------------------------------------------------------------------
Write-Step "Installation terminee"
Write-Host ""
if ($generatedAdminPassword) {
    Write-Host "  Compte administrateur (cree au premier demarrage du backend) :" -ForegroundColor Green
    Write-Host "     utilisateur  : $($envValues['ADMIN_USERNAME'])"
    Write-Host "     mot de passe : $generatedAdminPassword"
    Write-Host "  Notez-le ; il est aussi dans le fichier .env (ADMIN_PASSWORD)." -ForegroundColor Gray
    Write-Host ""
}
if (-not $envValues["GROQ_API_KEY"]) {
    Write-Warn "GROQ_API_KEY est vide : renseignez-la dans .env pour activer les reponses de l'IA."
}
Write-Host "  Lancement : double-cliquez sur scripts\demarrer.cmd" -ForegroundColor Cyan
Write-Host ""
try { Stop-Transcript | Out-Null } catch { }
exit 0
