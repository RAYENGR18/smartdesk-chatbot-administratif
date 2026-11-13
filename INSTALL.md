# Installation locale sous Windows

## 1. Prérequis

| Outil | Version | Téléchargement |
|-------|---------|----------------|
| Java (JDK) | 17 ou plus (21 conseillé) | https://adoptium.net |
| Node.js | 22 LTS (minimum 20.19) | https://nodejs.org |
| Python | 3.12 (3.11 à 3.13 acceptés) — cocher « Add python.exe to PATH » | https://www.python.org/downloads/ |
| PostgreSQL | 18 (15 à 17 fonctionnent aussi) | https://www.postgresql.org/download/windows/ |
| Clé Groq | gratuite | https://console.groq.com/keys |

À l'installation de PostgreSQL, **notez le mot de passe de l'utilisateur `postgres`** :
le script d'installation vous le demandera une fois pour créer la base.

## 2. Installation automatique

Double-cliquez sur **`scripts\installer.cmd`** (ou, dans PowerShell depuis le dossier du projet :
`powershell -ExecutionPolicy Bypass -File scripts\setup.ps1`).
Les saisies (mot de passe `postgres`, clé Groq) sont masquées : rien ne s'affiche pendant la frappe, c'est normal.
Le déroulé est enregistré dans `setup.log` (sans les mots de passe).

Le script :

1. vérifie Java, Node.js, Python et PostgreSQL ;
2. crée le fichier `.env` avec des secrets aléatoires (et vous demande votre clé Groq) ;
3. crée l'utilisateur PostgreSQL `smartdesk` et la base `chatbot_db` ;
4. installe les dépendances Python (dans `nlp\.venv`), npm et Maven, puis compile le backend.

Il affiche à la fin **le mot de passe du compte `admin`** (également dans `.env`, ligne `ADMIN_PASSWORD`).

Options utiles :

```powershell
# PostgreSQL installé ailleurs
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1 -PgBin "D:\PostgreSQL\18\bin"
# Relancer sans toucher à la base
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1 -SkipDatabase
```

## 3. Lancement

Double-cliquez sur **`scripts\demarrer.cmd`** (ou `powershell -ExecutionPolicy Bypass -File scripts\start.ps1`).

Trois fenêtres s'ouvrent (NLP, backend, frontend), puis le navigateur sur
http://localhost:61748.

> Au **premier** lancement, le service NLP télécharge le modèle d'embeddings
> (environ 500 Mo) et indexe la base de connaissances : comptez quelques minutes.

## 4. Lancement manuel (sans les scripts)

```powershell
# Service NLP
cd nlp
.\.venv\Scripts\python main.py

# Backend (autre fenêtre)
cd backend
.\mvnw.cmd spring-boot:run

# Frontend (autre fenêtre)
cd frontend
npm start
```

## 5. Premiers pas

1. Connectez-vous avec `admin` et le mot de passe `ADMIN_PASSWORD` du fichier `.env`.
2. Dans l'espace admin, générez un **identifiant** pour un étudiant ou un professeur
   (nom, prénom, CIN).
3. Sur la page d'inscription, l'étudiant/professeur saisit cet identifiant et son CIN.

## 6. Problèmes fréquents

| Symptôme | Solution |
|----------|----------|
| `JWT_SECRET manquant ou trop court` au démarrage du backend | Le fichier `.env` est absent ou incomplet : relancez `scripts\setup.ps1` |
| `password authentication failed for user "smartdesk"` | Relancez `scripts\setup.ps1` : il resynchronise le mot de passe de la base avec `.env` |
| Le chatbot répond toujours avec le même message générique | `GROQ_API_KEY` est vide ou invalide dans `.env` ; testez-la avec `cd nlp; .\.venv\Scripts\python verifier_groq.py` |
| `Cle API invalide` dans la fenêtre NLP | `NLP_API_KEY` doit être identique pour le backend et le NLP (même fichier `.env`) : redémarrez les deux |
| Le port 8080, 8000 ou 61748 est déjà utilisé | Fermez l'ancienne fenêtre du service. Pour changer de port : `SERVER_PORT` (backend) ou `NLP_PORT` + `NLP_URL` (NLP) dans `.env` |
| « Trop de tentatives » à la connexion | Protection anti brute-force : attendez 15 minutes ou redémarrez le backend |
| Mot de passe admin perdu | Dans psql (base `chatbot_db`) : `DELETE FROM chat_messages WHERE user_id = (SELECT id FROM users WHERE username='admin'); DELETE FROM documents WHERE user_id = (SELECT id FROM users WHERE username='admin'); DELETE FROM users WHERE username='admin';` puis redémarrez le backend : le compte est recréé avec `ADMIN_PASSWORD` du `.env` |
