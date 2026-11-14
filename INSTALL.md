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
2. **Registre (CIN)** : importez la liste officielle de l'établissement (Excel/CSV),
   ou cliquez **Charger les données de démonstration**.
3. Les étudiants et professeurs s'inscrivent eux-mêmes : leurs nom, prénom, CIN
   (+ filière et niveau) sont vérifiés automatiquement auprès du registre.
4. **Emplois du temps** : choisissez une filière et un niveau, puis « Générer ».
5. **Notes** : importez un fichier Excel/CSV (CIN, Matière, Note) — exemple dans `docs\exemples`.
6. **Présences** : branchez le lecteur RFID et lancez `scripts\pointage.cmd` (voir `iot\README.md`),
   ou utilisez la simulation dans *Admin → Présences profs*.

## 6. Problèmes fréquents

| Symptôme | Solution |
|----------|----------|
| `JWT_SECRET manquant ou trop court` au démarrage du backend | Le fichier `.env` est absent ou incomplet : relancez `scripts\setup.ps1` |
| `password authentication failed for user "smartdesk"` | Relancez `scripts\setup.ps1` : il resynchronise le mot de passe de la base avec `.env` |
| Le chatbot répond toujours avec le même message générique | `GROQ_API_KEY` est vide ou invalide dans `.env` ; testez-la avec `cd nlp; .\.venv\Scripts\python verifier_groq.py` |
| `Cle API invalide` dans la fenêtre NLP | `NLP_API_KEY` doit être identique pour le backend et le NLP (même fichier `.env`) : redémarrez les deux |
| « Le port 8095 (ou 8000, 61748) est déjà utilisé » | `demarrer.cmd` affiche le programme qui l'occupe. Fermez l'ancienne fenêtre du service, ou changez de port dans `.env` : `SERVER_PORT` (backend) ou `NLP_PORT` + `NLP_URL` (NLP) |
| « Ce numéro de CIN ne figure pas dans la base de l'établissement » | Le CIN n'est pas dans le registre : importez-le (Admin → Registre) ou chargez la démo |
| L'import des notes ignore des lignes « aucun compte étudiant » | L'étudiant de ce CIN n'a pas encore créé son compte : il faut qu'il s'inscrive d'abord |
| `pointage.cmd` : « Aucun port série détecté » | Branchez l'Arduino, fermez le moniteur série de l'IDE Arduino, ou indiquez le port : `pointage.cmd --port COM3` |
| « Trop de tentatives » à la connexion | Protection anti brute-force : attendez 15 minutes ou redémarrez le backend |
| Mot de passe admin perdu | Dans psql (base `chatbot_db`) : `DELETE FROM chat_messages WHERE user_id = (SELECT id FROM users WHERE username='admin'); DELETE FROM documents WHERE user_id = (SELECT id FROM users WHERE username='admin'); DELETE FROM users WHERE username='admin';` puis redémarrez le backend : le compte est recréé avec `ADMIN_PASSWORD` du `.env` |
