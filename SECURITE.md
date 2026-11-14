# Audit de sécurité et corrections

Audit du projet d'origine (`rania-gregba/projet-pfe-rania`, commit `a7e5954`)
et corrections apportées dans cette version.

## Failles critiques corrigées

| # | Problème dans la version d'origine | Correction |
|---|-----------------------------------|------------|
| 1 | **Aucune route de l'API n'était protégée** (`requestMatchers("/**").permitAll()`). Le token JWT était créé mais jamais vérifié : n'importe qui pouvait effacer la base (`DELETE /api/admin/reset`), passer un compte ADMIN, lire toutes les données personnelles. | Filtre JWT dans Spring Security, règles d'accès par rôle sur chaque route, et `@PreAuthorize` en double contrôle. |
| 2 | Le nom d'utilisateur venait **du corps de la requête** : envoi de messages, génération de documents et lecture d'historique au nom de n'importe qui. | L'utilisateur est toujours celui du token ; contrôle « soi-même ou admin » sur profils, historiques, notes, documents. |
| 3 | Mot de passe PostgreSQL et clé JWT **écrits dans `application.yml`** et dans l'historique Git. | Variables d'environnement (`.env`, jamais commité) ; secrets aléatoires générés par `setup.ps1` ; démarrage refusé si la clé JWT est absente ou trop courte. |
| 4 | Compte `admin / admin123` **réinitialisé à chaque démarrage**. | Compte créé une seule fois, mot de passe depuis `ADMIN_PASSWORD` (ou aléatoire affiché une fois). |
| 5 | **Traversée de répertoire** : le type de document servait de nom de fichier (`../../...`) ; idem pour les fichiers de la base de connaissances côté NLP. | Liste blanche des types ; noms de fichiers générés (UUID) ; validation stricte des noms + vérification que le chemin reste dans le dossier autorisé. |
| 6 | Service NLP ouvert à tous (CORS `*`, aucune authentification) : n'importe qui pouvait modifier la base de connaissances du chatbot. | Clé partagée `X-API-Key` obligatoire, plus de CORS, écoute sur `127.0.0.1`. |
| 7 | Téléchargement de n'importe quel document par son id (attestations d'autres étudiants). | Réservé au propriétaire ou à l'admin. |

## Autres corrections de sécurité

- Protection **brute-force** : blocage 15 min après 5 échecs (nom d'utilisateur + IP).
- Messages de connexion identiques que le compte existe ou non ; temps de réponse constant.
- Mots de passe : BCrypt (coût 12), 8 caractères minimum.
- Identifiants d'inscription générés avec `SecureRandom` (et non `Random`).
- Rôle `ADMIN` impossible à obtenir par l'inscription ; le dernier admin ne peut être ni supprimé ni rétrogradé.
- Remise à zéro de la base : confirmation obligatoire, comptes admin conservés.
- Le **CIN, l'adresse et le téléphone ne sont plus envoyés à l'IA** (Groq) : contexte construit côté serveur.
- Les réponses ne contiennent plus de chemins du serveur (`filePath`, sources RAG) ni de traces d'erreur.
- En-têtes HTTP de sécurité (`X-Frame-Options`, `Content-Security-Policy` de l'API, `Referrer-Policy`, `nosniff`).
- Validation des entrées et tailles maximales (messages, fichiers, champs de profil).
- Frontend : suppression des `console.log` qui affichaient la réponse de connexion (avec le token).
- Notifications : ciblage exact (l'ancien `LIKE '%1%'` envoyait au professeur 1 les notifications du professeur 12).

## Dépendances mises à jour

| Composant | Avant | Après | Raison |
|-----------|-------|-------|--------|
| Angular | 19.2 (8 vulnérabilités connues dont plusieurs XSS, plus maintenu) | 21.2 LTS | `npm audit` : 0 vulnérabilité |
| Spring Boot | 3.2.0 | 3.5.16 | Nombreux correctifs de sécurité Spring / Tomcat |
| jjwt | 0.11.5 | 0.12.6 | API actuelle |
| python-multipart | 0.0.6 | ≥ 0.0.18 | CVE-2024-24762 (déni de service) |
| pypdf | 3.17 | ≥ 6.0 | Correctifs de déni de service sur des PDF malveillants |
| chromadb / langchain | 0.4 / 0.1 | 1.x / 1.x | Versions maintenues, compatibles Python 3.11-3.13 |

## Bugs fonctionnels corrigés

- Réindexation de la base de connaissances impossible sous Windows (`shutil.rmtree` sur une base SQLite ouverte → WinError 32).
- `docx2txt` absent des dépendances : les fichiers `.docx` n'étaient jamais indexés.
- Suppression d'un utilisateur ayant déjà discuté avec le chatbot → erreur 500 (clé étrangère).
- Réponses longues du chatbot tronquées / refusées par la base (colonne limitée à 2000 caractères).
- Textes français et arabes mal encodés (`DÃ©connexion`, `Ø§...`) dans l'interface et les documents générés.
- Téléchargement de documents cassé une fois l'API protégée (`window.open` n'envoie pas le token).
- Mot de passe « trimé » à la connexion (un mot de passe avec espace final ne fonctionnait pas).
- `faq.txt` et `assistant_info.txt` de la base de connaissances avaient été écrasés par des
  contenus de test (« test », « Test contenu simple 123 ») : contenu d'origine restauré.

## Sécurité des évolutions (inscription CIN, notes, emplois du temps, RFID)

- **Inscription** : identité vérifiée côté serveur auprès du registre (jamais côté navigateur) ;
  nom, prénom, filière et niveau officiels recopiés depuis le registre ; un seul compte par CIN ;
  blocage après 10 vérifications refusées depuis la même adresse IP (anti-énumération des CIN).
- **Base externe de la faculté** : connexion en lecture seule, requête paramétrée (`?`), délai maximal,
  le backend démarre même si la base de la faculté est injoignable.
- **Notes** : `GET /api/notes/me` identifie l'étudiant par son token ; la réponse ne contient
  aucune donnée sur d'autres personnes.
- **Emplois du temps** : la filière et le niveau de l'étudiant sont lus en base par le serveur
  (`GET /api/planning/me`) ; la lecture d'une autre filière est réservée à l'admin et aux professeurs ;
  les créneaux n'exposent plus le CIN, le téléphone ni l'email des professeurs.
- **Lecteur RFID** : authentifié par une clé d'appareil (`RFID_DEVICE_KEY`, comparaison à temps constant),
  pas par un compte ; chaque passage est journalisé ; un badge ne peut appartenir qu'à un professeur.
- **Export CSV** des présences protégé contre l'injection de formules Excel.

## Données de démonstration

`DEMO_DATA=true` crée des comptes fictifs (CIN `99xxxxxx`) partageant le mot de passe
`DEMO_PASSWORD` : pratique pour tester, **à désactiver (`DEMO_DATA=false`) sur un serveur réel**.
La valeur par défaut côté serveur (sans `.env`) est `false`.

## Tests automatisés

- Backend : 22 tests (MockMvc + H2) couvrant l'inscription par CIN, l'import des notes (CSV/Excel),
  les emplois du temps par filière/niveau, le pointage RFID, et l'accès anonyme, les tokens falsifiés,
  les droits par rôle, l'accès aux données d'autrui, l'usurpation dans le chat,
  le brute-force, la traversée de répertoire, les documents, les notifications.
- Service NLP : 10 tests pytest (clé d'API, absence de CORS, chemins de fichiers,
  cycle complet d'un document de la base de connaissances).

## Pour aller plus loin (non fait)

- Passer à Spring Boot 4.x (la branche 3.5 n'a plus de correctifs gratuits depuis juin 2026).
- HTTPS et stockage du token en cookie `HttpOnly` pour un déploiement public.
- Migrations de schéma versionnées (Flyway) au lieu de `ddl-auto: update`.
