# SmartDesk — Chatbot administratif intelligent (FPST)

Portail administratif pour une faculté : chatbot multilingue (FR / EN / AR / derja),
génération de documents officiels (PDF / Word), gestion des notes, emplois du temps,
filières, salles et matières, inscription vérifiée par le CIN et pointage RFID des professeurs.

## Fonctionnalités

| Espace | Fonctionnalités |
|--------|-----------------|
| Étudiant | Inscription vérifiée par CIN, profil, **emploi du temps de sa filière et de son niveau**, **ses notes**, notifications, chatbot (RAG), génération de documents |
| Professeur | Inscription vérifiée par CIN, profil, son emploi du temps, notifications, chatbot, documents |
| Administrateur | Tableau de bord, **registre de l'établissement** (import Excel/CSV ou démo), utilisateurs, filières, matières (par niveau), salles, emplois du temps (génération par filière et niveau), **import des notes**, base de connaissances du chatbot, notifications, **présence des professeurs (RFID)** |

> Projet de fin d'études 2025/2026 de **Rania Gregba** et **Asma Mbarek**
> ([dépôt d'origine](https://github.com/rania-gregba/projet-pfe-rania)).
> Cette version est sécurisée, corrigée et mise à jour — voir [SECURITE.md](SECURITE.md).

## Architecture

```
Navigateur ──► Frontend Angular 21  (http://localhost:61748)
                   │  token JWT dans l'en-tête Authorization
                   ▼
               Backend Spring Boot 3.5 (http://localhost:8080) ──► PostgreSQL 18
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

## Démarrage rapide (Windows)

Prérequis : **Java 17+**, **Node.js 22 LTS**, **Python 3.12**, **PostgreSQL 18** —
détails dans [INSTALL.md](INSTALL.md).

1. **Première fois** : double-cliquer sur `scripts\installer.cmd`
   (crée `.env`, la base de données, installe les dépendances puis lance l'application).
2. **Les fois suivantes** : double-cliquer sur `scripts\demarrer.cmd`.

Équivalent en ligne de commande :

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
powershell -ExecutionPolicy Bypass -File scripts\start.ps1
```

Puis ouvrir http://localhost:61748 et se connecter avec le compte `admin`
(mot de passe : `ADMIN_PASSWORD` dans le fichier `.env`).

**Démonstration** : *Admin → Registre (CIN) → Charger les données de démonstration* crée 4 filières,
des matières, des salles et un registre fictif (CIN `99000101` à `99000116` pour les étudiants,
`99000201` à `99000206` pour les professeurs). Exemple : s'inscrire comme étudiant avec
*Ben Salah / Amine / 99000101 / Informatique / Licence 1*. Des fichiers de notes et de registre
d'exemple sont dans [docs/exemples](docs/exemples).

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
# Backend : 21 tests (sécurité, inscription par CIN, notes, emplois du temps, RFID — base H2 en mémoire)
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
scripts/    installer.cmd, demarrer.cmd, pointage.cmd (+ setup.ps1, start.ps1)
```
