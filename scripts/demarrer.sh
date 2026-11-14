#!/usr/bin/env bash
# =====================================================================
# SmartDesk - lancement des 3 services sous Linux et macOS
#
# Usage : ./scripts/demarrer.sh        (Ctrl+C arrete tout)
#
#   - NLP (FastAPI)     http://127.0.0.1:8000  (interne, appele par le backend)
#   - Backend (Spring)  http://localhost:8095  (SERVER_PORT dans .env)
#   - Frontend          http://localhost:61748 (relaie /api vers le backend)
# Journaux : dossier logs/
# =====================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1
[ -f .env ] || { echo "[ERREUR] Fichier .env absent : lancez d'abord ./scripts/installer.sh"; exit 1; }
[ -x nlp/.venv/bin/python ] || { echo "[ERREUR] Environnement Python absent : lancez d'abord ./scripts/installer.sh"; exit 1; }
[ -d frontend/node_modules ] || { echo "[ERREUR] Dependances npm absentes : lancez d'abord ./scripts/installer.sh"; exit 1; }

envval() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | tr -d '\r'; }
BACKEND_PORT=$(envval SERVER_PORT); [[ "$BACKEND_PORT" =~ ^[0-9]+$ ]] || BACKEND_PORT=8095
NLP_PORT=$(envval NLP_URL | sed -nE 's|.*:([0-9]+)/?$|\1|p'); [ -n "$NLP_PORT" ] || NLP_PORT=8000
FRONT_PORT=61748
mkdir -p logs

port_occupe() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }
attendre() { # url nom secondes
    local fin=$((SECONDS + $3))
    while [ $SECONDS -lt $fin ]; do
        if curl -sf -o /dev/null --max-time 3 "$1"; then printf '    \033[32m[OK]\033[0m %s pret\n' "$2"; return 0; fi
        sleep 3
    done
    printf '    \033[33m[!]\033[0m %s ne repond pas encore : voir logs/\n' "$2"; return 1
}

echo "==> Verification des ports"
if port_occupe "$BACKEND_PORT"; then
    echo "    [!] Le port $BACKEND_PORT (backend) est deja utilise."
    echo "        Arretez l'ancien lancement, ou choisissez un autre port dans .env (SERVER_PORT=8096 par exemple)."
    exit 1
fi
PIDS=()
arreter_arbre() { # arrete un processus et tous ses descendants (Maven lance un second Java)
    local enfant
    for enfant in $(pgrep -P "$1" 2>/dev/null); do arreter_arbre "$enfant"; done
    kill "$1" 2>/dev/null
}
arreter() {
    echo; echo "==> Arret de SmartDesk"
    for p in "${PIDS[@]}"; do arreter_arbre "$p"; done
    wait 2>/dev/null
    exit 0
}
trap arreter INT TERM

if port_occupe "$NLP_PORT"; then
    echo "    (le service NLP tourne deja sur le port $NLP_PORT : il n'est pas relance)"
else
    echo "==> Demarrage du service NLP (le premier lancement telecharge le modele, ~500 Mo)"
    (cd nlp && exec .venv/bin/python main.py) > logs/nlp.log 2>&1 &
    PIDS+=($!)
fi

echo "==> Demarrage du backend Spring Boot (port $BACKEND_PORT)"
chmod +x backend/mvnw
(cd backend && exec ./mvnw -q spring-boot:run) > logs/backend.log 2>&1 &
PIDS+=($!)

if port_occupe "$FRONT_PORT"; then
    echo "    (le frontend tourne deja sur le port $FRONT_PORT : il n'est pas relance)"
else
    echo "==> Demarrage du frontend Angular"
    (cd frontend && export NG_CLI_ANALYTICS=false SERVER_PORT="$BACKEND_PORT" && exec npm start) > logs/frontend.log 2>&1 &
    PIDS+=($!)
fi

echo
echo "==> Attente des services..."
attendre "http://localhost:$BACKEND_PORT/api/health" "Backend" 300
FRONT_OK=0
attendre "http://localhost:$FRONT_PORT" "Frontend" 240 && FRONT_OK=1
attendre "http://127.0.0.1:$NLP_PORT/health" "Service NLP" 300

echo
if [ $FRONT_OK -eq 1 ]; then
    printf '\033[32mSmartDesk est accessible sur http://localhost:%s\033[0m\n' "$FRONT_PORT"
    if command -v xdg-open >/dev/null 2>&1; then xdg-open "http://localhost:$FRONT_PORT" >/dev/null 2>&1 &
    elif command -v open >/dev/null 2>&1; then open "http://localhost:$FRONT_PORT"; fi
fi
echo "Connexion admin : utilisateur admin, mot de passe = ADMIN_PASSWORD dans .env"
if [ "$(envval DEMO_DATA)" = "true" ]; then
    echo "Comptes de demo : prenom.nom (ex. yasmine.trabelsi), mot de passe $(envval DEMO_PASSWORD)"
fi
echo "Journaux : logs/nlp.log, logs/backend.log, logs/frontend.log  -  Ctrl+C pour tout arreter"
wait
