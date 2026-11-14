#!/usr/bin/env bash
# =====================================================================
# SmartDesk - installation locale sous Linux et macOS
#
# Usage (depuis le dossier du projet) :   ./scripts/installer.sh
#
#   1. verifie Java 17+, Node.js 20.19+/22.12+, Python 3.11-3.13 et PostgreSQL ;
#   2. cree le fichier .env avec des secrets aleatoires ;
#   3. cree l'utilisateur et la base PostgreSQL ;
#   4. installe les dependances (Python, npm, Maven) et compile le backend ;
#   5. lance l'application (scripts/demarrer.sh).
# Peut etre relance sans risque : rien n'est ecrase.
#
# Variables utiles : PG_HOST (localhost), PG_PORT (5432), PG_SUPERUSER (postgres)
#                    SANS_DEMARRAGE=1 pour ne pas lancer l'application a la fin
# =====================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1
LOG="$ROOT/setup.log"
: > "$LOG"

PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5432}"
PG_SUPERUSER="${PG_SUPERUSER:-postgres}"

step() { printf '\n\033[36m==> %s\033[0m\n' "$1"; echo "==> $1" >> "$LOG"; }
ok()   { printf '    \033[32m[OK]\033[0m %s\n' "$1"; echo "    [OK] $1" >> "$LOG"; }
warn() { printf '    \033[33m[!]\033[0m %s\n' "$1"; echo "    [!] $1" >> "$LOG"; }
fail() { printf '\n\033[31m[ERREUR]\033[0m %s\n' "$1"; echo "[ERREUR] $1" >> "$LOG"; exit 1; }

secret() { # chaine aleatoire alphanumerique
    LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c "$1"
}

# ---------------------------------------------------------------------
step "Verification des prerequis"

command -v java >/dev/null 2>&1 || fail "Java 17 ou plus est introuvable (https://adoptium.net, ou : sudo apt install openjdk-21-jdk / brew install openjdk@21)"
JAVA_MAJOR=$(java -version 2>&1 | grep -m1 'version "' | sed -E 's/.*version "([0-9]+).*/\1/')
[ "${JAVA_MAJOR:-0}" -ge 17 ] 2>/dev/null || fail "Java $JAVA_MAJOR est trop ancien : Java 17 ou plus est requis (21 conseille)"
ok "Java $JAVA_MAJOR"
[ "$JAVA_MAJOR" -gt 25 ] && warn "Java $JAVA_MAJOR est plus recent que ce que supporte Spring Boot 3.5 : installez Java 21 en cas d'erreur de compilation."

command -v node >/dev/null 2>&1 || fail "Node.js est introuvable (version LTS : https://nodejs.org)"
NODE_V=$(node -v | sed 's/^v//')
NODE_MAJ=${NODE_V%%.*}; NODE_MIN=$(echo "$NODE_V" | cut -d. -f2)
if [ "$NODE_MAJ" -lt 20 ] || { [ "$NODE_MAJ" -eq 20 ] && [ "$NODE_MIN" -lt 19 ]; } || { [ "$NODE_MAJ" -eq 22 ] && [ "$NODE_MIN" -lt 12 ]; }; then
    fail "Node.js $NODE_V est trop ancien (minimum 20.19 ou 22.12)"
fi
ok "Node.js $NODE_V"

PYTHON=""
for p in python3.12 python3.11 python3.13 python3; do
    if command -v "$p" >/dev/null 2>&1; then
        v=$("$p" -c 'import sys; print("%d.%d" % sys.version_info[:2])' 2>/dev/null)
        case "$v" in 3.11|3.12|3.13) PYTHON=$(command -v "$p"); break ;; esac
    fi
done
[ -n "$PYTHON" ] || fail "Python 3.11, 3.12 ou 3.13 est introuvable (sudo apt install python3.12 python3.12-venv / brew install python@3.12)"
ok "Python : $PYTHON"

command -v psql >/dev/null 2>&1 || fail "psql (client PostgreSQL) est introuvable (sudo apt install postgresql / brew install postgresql@17)"
ok "PostgreSQL : $(psql --version)"

# ---------------------------------------------------------------------
step "Fichier .env"
ENV_FILE="$ROOT/.env"
SECRET_KEYS="DB_PASSWORD JWT_SECRET NLP_API_KEY RFID_DEVICE_KEY"
ADMIN_GENERE=""
if [ -f "$ENV_FILE" ]; then
    ajoutes=""
    while IFS= read -r ligne || [ -n "$ligne" ]; do
        if [[ "$ligne" =~ ^([A-Z_]+)=(.*)$ ]]; then
            cle="${BASH_REMATCH[1]}"; defaut="${BASH_REMATCH[2]}"
            if ! grep -q "^$cle=" "$ENV_FILE"; then
                case " $SECRET_KEYS " in *" $cle "*) defaut=$(secret 32) ;; esac
                printf '\n%s=%s\n' "$cle" "$defaut" >> "$ENV_FILE"
                ajoutes="$ajoutes $cle"
            fi
        fi
    done < "$ROOT/.env.example"
    if [ -n "$ajoutes" ]; then ok ".env complete avec :$ajoutes"; else ok ".env existe deja : conserve tel quel"; fi
else
    ADMIN_GENERE=$(secret 12)
    sed -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(secret 24)|" \
        -e "s|^JWT_SECRET=.*|JWT_SECRET=$(secret 48)|" \
        -e "s|^NLP_API_KEY=.*|NLP_API_KEY=$(secret 32)|" \
        -e "s|^RFID_DEVICE_KEY=.*|RFID_DEVICE_KEY=$(secret 32)|" \
        -e "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$ADMIN_GENERE|" \
        -e "s|^DB_URL=jdbc:postgresql://localhost:5432/|DB_URL=jdbc:postgresql://$PG_HOST:$PG_PORT/|" \
        "$ROOT/.env.example" > "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    if [ -t 0 ]; then
        echo "    Cle Groq (https://console.groq.com/keys) - collez-la puis Entree,"
        echo "    ou laissez vide pour la renseigner plus tard dans .env :"
        read -r -s -p "    GROQ_API_KEY : " GROQ; echo
        if [ -n "$GROQ" ]; then
            GROQ=$(printf '%s' "$GROQ" | tr -d '[:space:]|')
            sed -i.bak "s|^GROQ_API_KEY=.*|GROQ_API_KEY=$GROQ|" "$ENV_FILE" && rm -f "$ENV_FILE.bak"
        fi
    fi
    ok ".env cree avec des secrets aleatoires"
fi

envval() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | tr -d '\r'; }
DB_USER=$(envval DB_USERNAME); DB_PASS=$(envval DB_PASSWORD); DB_URL=$(envval DB_URL)
DB_NAME=$(echo "$DB_URL" | sed -E 's|.*/([A-Za-z0-9_]+)(\?.*)?$|\1|'); [ -n "$DB_NAME" ] || DB_NAME=chatbot_db
[[ "$DB_USER" =~ ^[A-Za-z_][A-Za-z0-9_]*$ && "$DB_NAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || fail "DB_USERNAME et le nom de base ne doivent contenir que lettres, chiffres et '_'"
[ -n "$DB_PASS" ] && [[ "$DB_PASS" != *"'"* ]] || fail "DB_PASSWORD vide ou invalide dans .env"

# ---------------------------------------------------------------------
step "Base de donnees PostgreSQL"
if PGPASSWORD="$DB_PASS" psql -h "$PG_HOST" -p "$PG_PORT" -U "$DB_USER" -d "$DB_NAME" -tAqw -c "select 1" >/dev/null 2>&1; then
    ok "Base '$DB_NAME' deja prete"
else
    # Connexion administrateur : sans mot de passe (macOS/Homebrew, trust, ~/.pgpass),
    # puis via le compte systeme postgres (Linux), puis en demandant le mot de passe.
    ADMIN_PSQL=()
    if psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_SUPERUSER" -d postgres -tAqw -c "select 1" >/dev/null 2>&1; then
        ADMIN_PSQL=(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_SUPERUSER")
    elif psql -p "$PG_PORT" -d postgres -tAqw -c "select 1" >/dev/null 2>&1; then
        ADMIN_PSQL=(psql -p "$PG_PORT")
    elif command -v sudo >/dev/null 2>&1 && id postgres >/dev/null 2>&1; then
        echo "    Creation de la base avec le compte systeme 'postgres' (sudo peut demander votre mot de passe)"
        if (cd /tmp && sudo -u postgres psql -p "$PG_PORT" -d postgres -tAq -c "select 1") >/dev/null 2>&1; then
            ADMIN_PSQL=(sudo -u postgres psql -p "$PG_PORT")
        fi
    fi
    if [ ${#ADMIN_PSQL[@]} -eq 0 ]; then
        [ -t 0 ] || fail "Connexion administrateur a PostgreSQL impossible (lancez le script dans un terminal)."
        read -r -s -p "    Mot de passe de l'utilisateur PostgreSQL '$PG_SUPERUSER' : " PGPASSWORD; echo
        export PGPASSWORD
        ADMIN_PSQL=(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_SUPERUSER")
    fi
    run_sql() { (cd /tmp && "${ADMIN_PSQL[@]}" -d "$1" -v ON_ERROR_STOP=1 -tAq -c "$2"); }

    run_sql postgres "select 1" >/dev/null 2>&1 || fail "Connexion a PostgreSQL impossible (serveur arrete ou mot de passe incorrect). Demarrage du serveur : sudo systemctl start postgresql / brew services start postgresql@17"
    if [ "$(run_sql postgres "SELECT 1 FROM pg_roles WHERE rolname = '$DB_USER'")" = "1" ]; then
        run_sql postgres "ALTER ROLE $DB_USER WITH LOGIN PASSWORD '$DB_PASS'" >/dev/null || fail "Mise a jour de l'utilisateur impossible"
        ok "Utilisateur '$DB_USER' existant : mot de passe synchronise avec .env"
    else
        run_sql postgres "CREATE ROLE $DB_USER WITH LOGIN PASSWORD '$DB_PASS'" >/dev/null || fail "Creation de l'utilisateur impossible"
        ok "Utilisateur '$DB_USER' cree"
    fi
    if [ "$(run_sql postgres "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'")" = "1" ]; then
        run_sql postgres "ALTER DATABASE $DB_NAME OWNER TO $DB_USER" >/dev/null
        ok "Base '$DB_NAME' existante : proprietaire = $DB_USER"
    else
        run_sql postgres "CREATE DATABASE $DB_NAME OWNER $DB_USER ENCODING 'UTF8' TEMPLATE template0" >/dev/null \
            || run_sql postgres "CREATE DATABASE $DB_NAME OWNER $DB_USER" >/dev/null || fail "Creation de la base impossible"
        ok "Base '$DB_NAME' creee"
    fi
    run_sql "$DB_NAME" "ALTER SCHEMA public OWNER TO $DB_USER" >/dev/null && ok "Schema public attribue a $DB_USER"
    unset PGPASSWORD
    PGPASSWORD="$DB_PASS" psql -h "$PG_HOST" -p "$PG_PORT" -U "$DB_USER" -d "$DB_NAME" -tAqw -c "select 1" >/dev/null 2>&1 \
        || fail "La base est creee mais '$DB_USER' ne peut pas s'y connecter par mot de passe sur $PG_HOST:$PG_PORT (verifiez pg_hba.conf : methode scram-sha-256 ou md5 pour 127.0.0.1)"
fi

# ---------------------------------------------------------------------
step "Service NLP : environnement Python (quelques minutes la premiere fois)"
VENV="$ROOT/nlp/.venv"
if [ ! -x "$VENV/bin/python" ]; then
    "$PYTHON" -m venv "$VENV" || fail "Creation de l'environnement Python impossible (Debian/Ubuntu : sudo apt install python3-venv)"
fi
VPY="$VENV/bin/python"
"$VPY" -m pip install --upgrade pip --quiet >> "$ROOT/pip-install.log" 2>&1
if [ "$(uname -s)" = "Linux" ] && ! "$VPY" -c "import torch" >/dev/null 2>&1; then
    # Version CPU de PyTorch (environ 200 Mo au lieu de plusieurs Go de bibliotheques CUDA)
    "$VPY" -m pip install torch --index-url https://download.pytorch.org/whl/cpu >> "$ROOT/pip-install.log" 2>&1 \
        || warn "PyTorch CPU indisponible : installation de la version standard"
fi
echo "    (journal : pip-install.log)"
"$VPY" -m pip install -r "$ROOT/nlp/requirements.txt" >> "$ROOT/pip-install.log" 2>&1 \
    || { tail -25 "$ROOT/pip-install.log"; fail "Installation des dependances Python impossible (detail : pip-install.log)"; }
"$VPY" -m spacy download fr_core_news_sm >> "$ROOT/pip-install.log" 2>&1 \
    || warn "Modele spaCy non installe : le service fonctionnera sans extraction d'entites."
ok "Dependances Python installees"

step "Frontend : dependances npm"
echo "    (journal : npm-install.log)"
(cd "$ROOT/frontend" && { npm ci --no-audit --no-fund > "$ROOT/npm-install.log" 2>&1 \
    || npm install --no-audit --no-fund >> "$ROOT/npm-install.log" 2>&1; }) \
    || { tail -30 "$ROOT/npm-install.log"; fail "Installation npm impossible (detail : npm-install.log)"; }
ok "Dependances npm installees"

step "Backend : compilation Maven"
echo "    (journal : backend-build.log)"
chmod +x "$ROOT/backend/mvnw"
(cd "$ROOT/backend" && ./mvnw -B -DskipTests package > "$ROOT/backend-build.log" 2>&1) \
    || { tail -40 "$ROOT/backend-build.log"; fail "La compilation du backend a echoue (detail : backend-build.log)"; }
ok "Backend compile"

# ---------------------------------------------------------------------
step "Installation terminee"
if [ -n "$ADMIN_GENERE" ]; then
    printf '\n  \033[32mCompte administrateur\033[0m : utilisateur admin, mot de passe %s\n' "$ADMIN_GENERE"
    echo "  (il est aussi dans le fichier .env, ligne ADMIN_PASSWORD)"
fi
[ -n "$(envval GROQ_API_KEY)" ] || warn "GROQ_API_KEY est vide : renseignez-la dans .env pour activer les reponses de l'IA."
if [ "$(envval DEMO_DATA)" = "true" ]; then
    echo "  Donnees de demonstration : chargees automatiquement au demarrage (DEMO_DATA=true)."
    echo "  Comptes de demo : prenom.nom (ex. yasmine.trabelsi), mot de passe $(envval DEMO_PASSWORD)"
fi
echo "  Lancement les fois suivantes : ./scripts/demarrer.sh"

if [ "${SANS_DEMARRAGE:-0}" != "1" ]; then
    exec "$ROOT/scripts/demarrer.sh"
fi
