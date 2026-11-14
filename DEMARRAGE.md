# Ouvrir SmartDesk sur n'importe quel PC

Guide rapide. Le détail et les problèmes fréquents sont dans [INSTALL.md](INSTALL.md).

Au premier démarrage, la base se **remplit toute seule avec des données fictives**
(`DEMO_DATA=true` dans `.env`). Vous pouvez donc tester tout de suite les trois espaces
(administrateur, étudiant, professeur), sans rien saisir.

---

## 1. Installer les prérequis (une seule fois par PC)

| Outil | Version | Windows | macOS (Homebrew) | Ubuntu / Debian |
|-------|---------|---------|------------------|-----------------|
| Git | récente | https://git-scm.com | `brew install git` | `sudo apt install git` |
| Java (JDK) | 17 à 25 (21 conseillé) | https://adoptium.net | `brew install openjdk@21` | `sudo apt install openjdk-21-jdk` |
| Node.js | 22 LTS (min. 20.19) | https://nodejs.org | `brew install node@22` | https://nodejs.org (ou `nvm install 22`) |
| Python | 3.12 (3.11 à 3.13) | https://www.python.org/downloads/ — cocher « Add python.exe to PATH » | `brew install python@3.12` | `sudo apt install python3.12 python3.12-venv` |
| PostgreSQL | 15 à 18 | https://www.postgresql.org/download/windows/ — **notez le mot de passe `postgres`** | `brew install postgresql@17` puis `brew services start postgresql@17` | `sudo apt install postgresql` |

Facultatif : une clé Groq gratuite (https://console.groq.com/keys) pour les réponses du chatbot.

## 2. Récupérer le projet

```bash
git clone https://github.com/RAYENGR18/smartdesk-chatbot-administratif.git
cd smartdesk-chatbot-administratif
```

(ou bouton **Code → Download ZIP** sur GitHub, puis décompresser)

## 3. Installer et lancer

**Windows** : double-cliquez sur **`scripts\installer.cmd`**.

**macOS / Linux** : dans un terminal, depuis le dossier du projet :

```bash
./scripts/installer.sh
```

Le script vérifie les prérequis, crée le fichier `.env` (secrets aléatoires), crée la base
PostgreSQL, installe les dépendances, compile, puis lance l'application.
Il demande au plus deux choses : le mot de passe `postgres` (si nécessaire) et la clé Groq
(facultative, Entrée pour passer). La première installation prend 5 à 15 minutes.

Le site s'ouvre ensuite sur **http://localhost:61748**.

## 4. Les fois suivantes

- **Windows** : double-clic sur **`scripts\demarrer.cmd`** (fermez les 3 fenêtres pour arrêter)
- **macOS / Linux** : `./scripts/demarrer.sh` (**Ctrl+C** pour tout arrêter)

## 5. Se connecter

| Rôle | Identifiant | Mot de passe |
|------|-------------|--------------|
| Administrateur | `admin` | ligne `ADMIN_PASSWORD` du fichier `.env` (affiché aussi à la fin de l'installation) |
| Étudiante (Informatique L1) | `yasmine.trabelsi` | `Demo2026` |
| Étudiant (Informatique L2) | `mehdi.gharbi` | `Demo2026` |
| Étudiante (Chimie L1) | `ines.hammami` | `Demo2026` |
| Professeur (Informatique) | `karim.haddad` | `Demo2026` |
| Professeure (Mathématiques) | `hela.chebbi` | `Demo2026` |

Tous les comptes de démonstration suivent le modèle `prenom.nom`, avec le mot de passe
`DEMO_PASSWORD` du fichier `.env` (`Demo2026` par défaut).

**Essayer l'inscription par CIN** : « Créer un compte » → Étudiant →
*Ben Salah / Amine / CIN 99000101 / Informatique / Licence 1* (accepté), puis le même
avec une autre filière ou un CIN inventé (refusé). Pour un professeur : *Saidi / Mounir / 99000209*.

**Fiches** : en administrateur, menu *Fiches étudiants / profs* (ou bouton « Fiche » dans la liste
des utilisateurs) : CIN, identité, registre, notes et moyennes, emploi du temps, documents ; pour un
professeur, ses matières, son badge et ses présences. Bouton « Imprimer » pour une version papier.

## 6. Ce que contient la démonstration

- 4 filières (Informatique, Chimie, Mathématiques appliquées, Gestion), 22 matières, 8 salles
- un registre de 25 personnes (CIN `99000101` à `99000116`, `99000201` à `99000209`)
- 15 comptes étudiants et 8 comptes professeurs déjà créés
- les emplois du temps de chaque filière et de chaque niveau
- les notes (DS + examen) de chaque étudiant
- les badges RFID des professeurs et deux semaines d'historique de présence (+ la journée en cours)
- des notifications pour les étudiants et les professeurs

Rien n'est dupliqué ni écrasé d'un démarrage à l'autre. Pour démarrer **sans** données fictives
(vraie utilisation) : mettez `DEMO_DATA=false` dans `.env` **avant** le premier lancement.

## 7. En cas de problème

| Symptôme | Solution |
|----------|----------|
| « Le port 8095 est déjà utilisé » | Fermez l'ancien lancement, ou `SERVER_PORT=8096` dans `.env` |
| La page s'ouvre mais la connexion échoue | Attendez que le backend ait fini de démarrer (1 à 2 minutes), puis réessayez |
| Mot de passe admin oublié | Nouveau mot de passe (8 caractères min.) sur la ligne `ADMIN_PASSWORD` de `.env`, puis redémarrez |
| Le chatbot ne répond pas | Renseignez `GROQ_API_KEY` dans `.env`, puis redémarrez |
| Une étape de l'installation échoue | Lisez `setup.log` (et `npm-install.log`, `pip-install.log`, `backend-build.log`) |
