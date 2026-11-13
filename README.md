# SmartDesk — Chatbot administratif intelligent (FPST)

Portail administratif pour une faculté : chatbot multilingue (FR / EN / AR / derja),
génération de documents officiels (PDF / Word), gestion des notes, emplois du temps,
filières, salles et matières.

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

## Rôles

| Rôle | Accès |
|------|-------|
| `ADMIN` | Tableau de bord, utilisateurs, identifiants d'inscription, notes (import Excel/CSV), filières, salles, matières, emplois du temps, base de connaissances, notifications |
| `PROFESSEUR` | Son espace, son emploi du temps, ses notifications, le chatbot, ses documents |
| `USER` (étudiant) | Son espace, ses notes, l'emploi du temps de sa filière, ses notifications, le chatbot, ses documents |

L'inscription n'est possible qu'avec un **identifiant généré par l'administration**
(12 chiffres pour un étudiant, 8 pour un professeur) et le CIN correspondant.

## Configuration

Toute la configuration passe par le fichier `.env` à la racine (modèle : [.env.example](.env.example)).
Il contient les secrets (mot de passe de la base, clé JWT, clé Groq…) et **n'est jamais commité**.

## Tests

```powershell
# Backend : 15 tests (sécurité de l'API, base H2 en mémoire)
cd backend; .\mvnw.cmd test

# Service NLP : 10 tests (clé d'API, chemins de fichiers, base de connaissances)
cd nlp; .\.venv\Scripts\python -m pip install pytest httpx; .\.venv\Scripts\python -m pytest tests
```

## Structure

```
backend/    API Spring Boot (controller, service, security, model, repository)
frontend/   Application Angular (pages, services, core = intercepteur + guards)
nlp/        Service FastAPI + base de connaissances (knowledge_base/)
scripts/    setup.ps1 (installation) et start.ps1 (lancement)
```
