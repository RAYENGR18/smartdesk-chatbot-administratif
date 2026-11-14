# Pointage RFID des professeurs

Chaque professeur reçoit un badge RFID. Un lecteur placé à l'entrée enregistre :

- **1er passage de la journée** : heure d'arrivée ;
- **2e passage** : heure de départ (la présence de la journée est clôturée) ;
- passages suivants : ignorés (« déjà pointé »). Deux passages à moins de 60 s comptent pour un seul.

L'administrateur consulte le récapitulatif (nom, date, arrivée, départ, durée) dans
**Admin → Présences profs**, l'exporte vers Excel, et y attribue les badges.

## Matériel

| Élément | Remarque |
|---------|----------|
| Arduino Uno (ou Nano) + câble USB | branché au PC de l'accueil |
| Module RC522 (MFRC522, 13,56 MHz) + badges/cartes MIFARE | souvent vendus ensemble |
| LED verte, LED rouge (+ 2 résistances 220 Ω), buzzer actif | facultatif : retour visuel/sonore |

### Câblage (Arduino Uno)

| RC522 | Arduino |
|-------|---------|
| SDA (SS) | D10 |
| SCK | D13 |
| MOSI | D11 |
| MISO | D12 |
| RST | D9 |
| **3.3V** | **3.3V** (pas 5 V !) |
| GND | GND |
| IRQ | non connecté |

LED verte → D5, LED rouge → D6, buzzer → D7 (chacun avec sa masse sur GND).

## Installation

1. **Arduino IDE** → Outils → Gérer les bibliothèques → installer **MFRC522** (GithubCommunity).
2. Ouvrir `iot/arduino/lecteur_rfid/lecteur_rfid.ino`, choisir la carte et le port, puis **Téléverser**.
   Dans le moniteur série (9600 bauds), un badge présenté affiche `UID:04A13B2C`.
   *Fermez le moniteur série ensuite : un seul programme peut utiliser le port.*
3. Sur le PC de l'accueil, la plateforme étant lancée, double-cliquer sur **`scripts\pointage.cmd`**.
   L'Arduino est détecté automatiquement (sinon : `scripts\pointage.cmd --port COM3`,
   ou `RFID_PORT=COM3` dans `.env`).

## Attribuer les badges

Dans **Admin → Présences profs** :

- passez un badge neuf devant le lecteur : il apparaît dans **Badges inconnus** ;
- choisissez le professeur et cliquez **OK**. C'est fait.

## Sans matériel (démonstration)

- **Admin → Présences profs → Simulation** : un clic = un passage de badge ;
- ou en ligne de commande : `scripts\pointage.cmd --test 04A13B2C`.

## Autres lecteurs

- **Lecteur USB « clavier »** (il tape l'UID puis Entrée) : `scripts\pointage.cmd --clavier`,
  en gardant la fenêtre au premier plan.
- **ESP32 en Wi-Fi** : envoyer `POST /api/iot/rfid/scan` avec l'en-tête `X-Device-Key`
  (valeur `RFID_DEVICE_KEY` du `.env`) et le corps `{"uid":"04A13B2C","lecteur":"ENTREE"}`.

## Sécurité

- Le lecteur s'authentifie avec la clé `RFID_DEVICE_KEY` (générée par `setup.ps1`), jamais avec un compte utilisateur.
- Un badge ne peut être attribué qu'à un seul professeur ; chaque passage est journalisé (y compris les badges inconnus).
- Les badges RFID simples (UID) peuvent être copiés : pour un usage réel, combinez avec une caméra
  ou utilisez des cartes MIFARE DESFire.
