#!/usr/bin/env bash
# =====================================================================
# SmartDesk dans Docker (macOS / Linux)
#
# Usage : ./scripts/docker-demarrer.sh
#   1. verifie Docker ;
#   2. cree le fichier .env avec des secrets aleatoires (s'il n'existe pas) ;
#   3. construit et demarre les conteneurs (docker compose up -d --build) ;
#   4. attend que le site reponde puis l'ouvre dans le navigateur.
# Arret : ./scripts/docker-arreter.sh
# =====================================================================
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

fail() { printf '\n\033[31m[ERREUR]\033[0m %s\n' "$1"; exit 1; }
ok()   { printf '    \033[32m[OK]\033[0m %s\n' "$1"; }
secret() { LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c "$1"; }

echo "==> Docker"
command -v docker >/dev/null 2>&1 || fail "Docker est introuvable : https://docs.docker.com/get-docker/"
if ! docker info >/dev/null 2>&1; then
    if [ "$(uname -s)" = "Darwin" ]; then open -a Docker 2>/dev/null; fi
    echo "    En attente du moteur Docker (3 minutes maximum)..."
    for _ in $(seq 1 36); do docker info >/dev/null 2>&1 && break; sleep 5; done
    docker info >/dev/null 2>&1 || fail "Docker ne repond pas (Linux : sudo systemctl start docker ; ajoutez votre utilisateur au groupe docker)."
fi
docker compose version >/dev/null 2>&1 || fail "'docker compose' est introuvable : installez le plugin Docker Compose v2."
ok "Docker $(docker version --format '{{.Server.Version}}' 2>/dev/null)"

echo "==> Fichier .env"
ADMIN_GENERE=""
if [ -f .env ]; then
    ajoutes=""
    while IFS= read -r ligne || [ -n "$ligne" ]; do
        if [[ "$ligne" =~ ^([A-Z_]+)=(.*)$ ]]; then
            cle="${BASH_REMATCH[1]}"; defaut="${BASH_REMATCH[2]}"
            if ! grep -q "^$cle=" .env; then
                case "$cle" in DB_PASSWORD|JWT_SECRET|NLP_API_KEY|RFID_DEVICE_KEY) defaut=$(secret 32) ;; esac
                printf '\n%s=%s\n' "$cle" "$defaut" >> .env
                ajoutes="$ajoutes $cle"
            fi
        fi
    done < .env.example
    if [ -n "$ajoutes" ]; then ok ".env complete avec :$ajoutes"; else ok ".env existe deja : conserve tel quel"; fi
else
    ADMIN_GENERE=$(secret 12)
    sed -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(secret 24)|" \
        -e "s|^JWT_SECRET=.*|JWT_SECRET=$(secret 48)|" \
        -e "s|^NLP_API_KEY=.*|NLP_API_KEY=$(secret 32)|" \
        -e "s|^RFID_DEVICE_KEY=.*|RFID_DEVICE_KEY=$(secret 32)|" \
        -e "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$ADMIN_GENERE|" \
        .env.example > .env
    chmod 600 .env
    ok ".env cree avec des secrets aleatoires"
fi
envval() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | tr -d '\r'; }
PORT=$(envval APP_PORT); [[ "$PORT" =~ ^[0-9]+$ ]] || PORT=8090
URL="http://localhost:$PORT"

echo "==> Construction et demarrage des conteneurs"
echo "    La premiere fois : 10 a 20 minutes. Les fois suivantes : quelques secondes."
docker compose up -d --build 2>&1 | tee docker-build.log | grep -E "^ *(Container|Network|Volume|Image|=>.*DONE)|ERROR|error" | tail -20
[ "${PIPESTATUS[0]}" -eq 0 ] || fail "Le demarrage des conteneurs a echoue (detail : docker-build.log)."
ok "Conteneurs demarres"

echo "==> Attente du site ($URL)"
OK=0
for _ in $(seq 1 60); do
    if curl -sf -o /dev/null --max-time 5 "$URL/api/health"; then OK=1; break; fi
    sleep 5
done
docker compose ps
echo
if [ $OK -eq 1 ]; then
    printf '\033[32mSmartDesk est accessible sur %s\033[0m\n' "$URL"
    if command -v xdg-open >/dev/null 2>&1; then xdg-open "$URL" >/dev/null 2>&1 &
    elif command -v open >/dev/null 2>&1; then open "$URL"; fi
else
    echo "[!] Le site ne repond pas encore. Journaux : docker compose logs backend"
fi
if [ -n "$ADMIN_GENERE" ]; then
    echo "Compte administrateur : admin / $ADMIN_GENERE   (aussi dans .env, ligne ADMIN_PASSWORD)"
else
    echo "Compte administrateur : admin, mot de passe = ADMIN_PASSWORD dans .env"
fi
[ "$(envval DEMO_DATA)" = "false" ] || echo "Comptes de demo : prenom.nom (ex. yasmine.trabelsi), mot de passe $(envval DEMO_PASSWORD)"
[ -n "$(envval GROQ_API_KEY)" ] || echo "[!] GROQ_API_KEY est vide : renseignez-la dans .env puis relancez ce script pour activer l'IA."
echo "Le chatbot est pret quelques minutes apres le premier demarrage (telechargement du modele)."
echo "Arret : ./scripts/docker-arreter.sh  -  les donnees sont conservees entre deux demarrages."
