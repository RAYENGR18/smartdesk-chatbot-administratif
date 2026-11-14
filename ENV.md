# Variables d'environnement (fichier `.env`)

Toute la configuration de SmartDesk est dans un fichier **`.env`** à la racine du projet.
Il n'est **jamais** envoyé sur GitHub : chaque PC a le sien.

**Vous n'avez rien à écrire à la main pour démarrer.** Le script de lancement crée le `.env`
à partir de [`.env.example`](.env.example) et génère lui-même les secrets :
`scripts\docker-demarrer.cmd` (Docker) ou `scripts\installer.cmd` (installation classique).

---

## 1. À récupérer vous-même

| Variable | À quoi elle sert | Où la récupérer |
|----------|------------------|-----------------|
| `GROQ_API_KEY` | Réponses de l'IA du chatbot. Sans elle, le site marche mais le chatbot répond de façon limitée. | Gratuit : créer un compte sur https://console.groq.com/keys → **Create API Key** → copier la clé (`gsk_...`) |
| `REGISTRE_DB_URL`<br>`REGISTRE_DB_USERNAME`<br>`REGISTRE_DB_PASSWORD`<br>`REGISTRE_DB_REQUETE` | **Seulement si** `REGISTRE_MODE=externe` : vérifier les inscriptions directement dans la base de la faculté | Service informatique de la faculté : adresse de la base, un compte **en lecture seule**, et la requête (exemple dans le [README](README.md#inscription-vérifiée-par-le-cin)) |

Après avoir modifié `.env`, relancez SmartDesk (`docker-demarrer` ou `demarrer`).

## 2. Générées automatiquement (ne pas partager)

| Variable | À quoi elle sert |
|----------|------------------|
| `DB_PASSWORD` | Mot de passe de la base PostgreSQL |
| `JWT_SECRET` | Signature des sessions de connexion |
| `NLP_API_KEY` | Clé entre le backend et le service du chatbot |
| `RFID_DEVICE_KEY` | Clé du lecteur de badges (pointage des professeurs) |
| `ADMIN_PASSWORD` | Mot de passe du compte `admin`. Pour le changer : nouvelle valeur (8 caractères min.) puis redémarrage |

## 3. Réglages facultatifs (valeurs par défaut correctes)

| Variable | Défaut | Rôle |
|----------|--------|------|
| `APP_PORT` | `8090` | Port du site avec Docker (http://localhost:8090) |
| `SERVER_PORT` | `8095` | Port du backend (installation classique) |
| `DEMO_DATA` | `true` | Remplit la base avec des données fictives. **`false` pour une vraie utilisation** |
| `DEMO_PASSWORD` | `Demo2026` | Mot de passe des comptes de démo (`prenom.nom`) |
| `ADMIN_USERNAME` / `ADMIN_EMAIL` | `admin` / `admin@fpst.tn` | Compte administrateur |
| `REGISTRE_MODE` | `local` | `local` = registre importé (Excel/CSV) ; `externe` = base de la faculté |
| `JWT_EXPIRATION_MS` | `28800000` | Durée d'une session (8 h) |
| `RFID_PORT` | vide | Port de l'Arduino (`COM3`…) ; vide = détection automatique |
| `BACKEND_URL` | `http://localhost:8095` | Adresse utilisée par le pointage RFID ; avec Docker : `http://localhost:8090` |
| `DB_URL`, `DB_USERNAME`, `NLP_URL`, `CORS_ALLOWED_ORIGINS`, `DOCUMENTS_DIR` | voir `.env.example` | Installation classique uniquement (Docker les fixe lui-même) |

## Règles

- Format : `CLE=valeur`, sans guillemets ni espaces autour du `=`.
- Ne jamais publier ni envoyer le fichier `.env` (il contient les secrets).
- Nouveau PC : ne copiez pas le `.env` d'un autre PC, laissez le script en générer un.
  Reportez seulement `GROQ_API_KEY` (et les `REGISTRE_DB_*` si besoin).
