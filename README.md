# SmartDesk — Chatbot administratif intelligent (FPST)

Portail administratif pour une faculté : chatbot multilingue (FR / EN / AR / derja),
génération de documents officiels (PDF / Word), gestion des notes, emplois du temps,
filières, salles et matières, inscription vérifiée par le CIN et pointage RFID des professeurs.

## Fonctionnalités

| Espace | Fonctionnalités |
|--------|-----------------|
| Étudiant | Inscription vérifiée par CIN, profil, **emploi du temps de sa filière et de son niveau**, **ses notes**, notifications, chatbot (RAG), génération de documents |
| Professeur | Inscription vérifiée par CIN, profil, son emploi du temps, notifications, chatbot, documents |
| Administrateur | Tableau de bord, **fiches complètes des étudiants et des professeurs** (CIN, identité, notes et moyennes, emploi du temps, présences, documents — imprimables), **registre de l'établissement** (import Excel/CSV ou démo), utilisateurs, filières, matières (par niveau), salles, emplois du temps (génération par filière et niveau), **import des notes**, base de connaissances du chatbot, notifications, **présence des professeurs (RFID)** |

> Projet de fin d'études 2025/2026 de **Rania Gregba** et **Asma Mbarek**
> ([dépôt d'origine](https://github.com/rania-gregba/projet-pfe-rania)).
> Cette version est sécurisée, corrigée et mise à jour — voir [SECURITE.md](SECURITE.md).

## Architecture

```
Navigateur ──► Frontend Angular 21  (http://localhost:61748)
                   │  token JWT dans l'en-tête Authorization
                   ▼
               Backend Spring Boot 3.5 (http://localhost:8095) ──► PostgreSQL 18
                   │  clé X-API-Key
                   ▼
               Service NLP FastAPI (http://127.0.0.1:8000, local uniquement)
                   ├─ ChromaDB + embeddings multilingues (RAG)
                   └─ API Groq (Llama 3.3)
```

| Partie   | Technologies |
|----------|--------------|
| Frontend | Angular 21, TypeScript 5.9 |
| Backend  | Spring Boot 3.5.16, Java 17+, Spring Security, JWT (jjwt 0.12), JPA |
| Base     | PostgreSQL 15 à 18 |
| NLP      | Python 3.11–3.13, FastAPI, LangChain, ChromaDB, sentence-transformers, spaCy, Groq |

## Démarrage rapide (Windows, macOS, Linux)

👉 **Guide pas à pas : [DEMARRAGE.md](DEMARRAGE.md)**

**Avec Docker** (seul prérequis : Docker Desktop) — site sur http://localhost:8090 :

| | Windows | macOS / Linux |
|---|---------|---------------|
| Lancer (la 1re fois : construit les images) | double-clic sur `scripts\docker-demarrer.cmd` | `./scripts/docker-demarrer.sh` |
| Arrêter (données conservées) | double-clic sur `scripts\docker-arreter.cmd` | `./scripts/docker-arreter.sh` |

**Sans Docker** (Java, Node.js, Python, PostgreSQL installés) — site sur http://localhost:61748 :

| | Windows | macOS / Linux |
|---|---------|---------------|
| Première fois (installe puis lance) | double-clic sur `scripts\installer.cmd` | `./scripts/installer.sh` |
| Les fois suivantes | double-clic sur `scripts\demarrer.cmd` | `./scripts/demarrer.sh` |

**Données de démonstration** : avec `DEMO_DATA=true` (valeur par défaut du `.env` créé par l'installation),
la base se remplit toute seule au démarrage avec des données fictives : filières, matières, salles,
registre (CIN `99xxxxxx`), 15 comptes étudiants et 8 comptes professeurs (`prenom.nom` /
`Demo2026`, ex. `yasmine.trabelsi`, `karim.haddad`), emplois du temps, notes, présences RFID et
notifications. Rien n'est dupliqué d'un démarrage à l'autre ; mettez `DEMO_DATA=false` pour une
vraie utilisation. Le même chargement est disponible dans *Admin → Registre (CIN)*.

Compte administrateur : `admin`, mot de passe `ADMIN_PASSWORD` du fichier `.env`.
Des fichiers de notes et de registre d'exemple sont dans [docs/exemples](docs/exemples).

## Rôles

| Rôle | Accès |
|------|-------|
| `ADMIN` | Tableau de bord, utilisateurs, identifiants d'inscription, notes (import Excel/CSV), filières, salles, matières, emplois du temps, base de connaissances, notifications |
| `PROFESSEUR` | Son espace, son emploi du temps, ses notifications, le chatbot, ses documents |
| `USER` (étudiant) | Son espace, ses notes, l'emploi du temps de sa filière, ses notifications, le chatbot, ses documents |

## Inscription vérifiée par le CIN

L'utilisateur choisit son profil (étudiant ou professeur) et saisit nom, prénom, CIN
(+ filière et niveau pour un étudiant). Ces informations sont comparées à la **base de
l'établissement** : l'inscription n'est acceptée que si le CIN existe et que tout correspond
(majuscules, accents et zéro initial du CIN tolérés). Un seul compte par CIN.

La source est configurable dans `.env` (`REGISTRE_MODE`) :

- `local` (par défaut) : registre importé par l'admin (Excel/CSV : `cin;nom;prenom;type;filiere;niveau;email;matricule;departement`) ;
- `externe` : lecture directe de la base de la faculté, avec un compte en lecture seule :

```properties
REGISTRE_MODE=externe
REGISTRE_DB_URL=jdbc:postgresql://serveur-fac:5432/scolarite
REGISTRE_DB_USERNAME=lecture_smartdesk
REGISTRE_DB_PASSWORD=...
REGISTRE_DB_REQUETE=SELECT cin, nom, prenom, type, filiere, niveau, email, matricule, departement FROM vue_personnes WHERE cin = ?
```

## Notes

*Admin → Notes → Importer* un fichier `.xlsx` ou `.csv` avec au minimum les colonnes **CIN**, **Matière**, **Note**
(facultatif : Type, Moyenne, Commentaire). Chaque note est rattachée à l'étudiant par son CIN ;
une note existante (même matière et même type) est mise à jour. Chaque étudiant ne voit que ses notes.

## Emplois du temps

Chaque créneau appartient à une **filière et un niveau**. L'étudiant voit uniquement l'emploi du temps
de sa filière et de son niveau, déterminés par le serveur à partir de son compte.

## Présence des professeurs (RFID)

Lecteur RC522 sur Arduino branché au PC de l'accueil + `scripts\pointage.cmd`.
1er passage du badge = arrivée, 2e = départ. Récapitulatif, export et attribution des badges dans
*Admin → Présences profs*. Montage et installation : [iot/README.md](iot/README.md).

## Configuration

Toute la configuration passe par le fichier `.env` à la racine (modèle : [.env.example](.env.example)).
Il contient les secrets (mot de passe de la base, clé JWT, clé Groq…) et **n'est jamais commité**.

## Tests

```powershell
# Backend : 23 tests (sécurité, compte admin, fiches, inscription par CIN, notes, emplois du temps, RFID — base H2 en mémoire)
cd backend; .\mvnw.cmd test

# Service NLP : 10 tests (clé d'API, chemins de fichiers, base de connaissances)
cd nlp; .\.venv\Scripts\python -m pip install pytest httpx; .\.venv\Scripts\python -m pytest tests
```

## Structure

```
backend/    API Spring Boot (controller, service, security, model, repository)
frontend/   Application Angular (pages, services, core = intercepteur + guards)
nlp/        Service FastAPI + base de connaissances (knowledge_base/)
iot/        Lecteur RFID : programme Arduino + pont Python (pont_rfid.py)
docs/       Fichiers d'exemple (registre, notes)
scripts/    Docker : docker-demarrer.cmd/.sh, docker-arreter.cmd/.sh
            Windows : installer.cmd, demarrer.cmd, pointage.cmd (+ setup.ps1, start.ps1)
            macOS / Linux : installer.sh, demarrer.sh
docker-compose.yml   PostgreSQL + NLP + backend + frontend (nginx) ; Dockerfile dans chaque dossier
```
