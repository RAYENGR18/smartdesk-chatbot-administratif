#!/usr/bin/env bash
# SmartDesk dans Docker : arret des conteneurs (les donnees sont conservees)
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" || exit 1
docker compose stop
echo "SmartDesk est arrete. Relancer : ./scripts/docker-demarrer.sh"
echo "Tout effacer (base comprise) : docker compose down -v"
