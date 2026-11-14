"""
SmartDesk - pont entre le lecteur RFID (Arduino + RC522) et la plateforme.

L'Arduino envoie une ligne "UID:04A13B2C" sur le port USB (série) à chaque
badge présenté. Ce programme transmet l'identifiant au backend
(POST /api/iot/rfid/scan, en-tête X-Device-Key), affiche le résultat
et renvoie un signal à l'Arduino pour les LED / le buzzer :
    A = arrivée   D = départ   X = déjà pointé / doublon   E = badge inconnu ou erreur

Utilisation (depuis le dossier du projet) :
    scripts\\pointage.cmd                       Arduino détecté automatiquement
    scripts\\pointage.cmd --port COM4           port imposé
    scripts\\pointage.cmd --clavier             lecteur USB "clavier" (saisie au clavier)
    scripts\\pointage.cmd --test 04A13B2C       envoie un seul passage (test sans matériel)

Configuration lue dans le fichier .env à la racine :
    RFID_DEVICE_KEY  (obligatoire)   BACKEND_URL (défaut http://localhost:8095)
    RFID_PORT        (ex : COM3)     RFID_LECTEUR (défaut ENTREE)
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SIGNAUX = {"ARRIVEE": "A", "DEPART": "D", "DEJA_CLOTURE": "X", "DOUBLON": "X", "INCONNU": "E"}

# Couleurs dans la console Windows 10+
if os.name == "nt":
    os.system("")
VERT, ROUGE, JAUNE, BLEU, RESET = "\033[92m", "\033[91m", "\033[93m", "\033[96m", "\033[0m"


def lire_env():
    """Lecture minimale du .env (pas de dépendance externe)."""
    valeurs = {}
    chemin = os.path.join(RACINE, ".env")
    if os.path.exists(chemin):
        with open(chemin, encoding="utf-8-sig") as f:
            for ligne in f:
                ligne = ligne.strip()
                if ligne and not ligne.startswith("#") and "=" in ligne:
                    cle, val = ligne.split("=", 1)
                    valeurs[cle.strip()] = val.strip()
    for cle in ("RFID_DEVICE_KEY", "BACKEND_URL", "RFID_PORT", "RFID_LECTEUR"):
        if os.environ.get(cle):
            valeurs[cle] = os.environ[cle]
    return valeurs


def horodatage():
    return datetime.now().strftime("%H:%M:%S")


def envoyer(url, cle, uid, lecteur):
    """Envoie un passage au backend ; renvoie (resultat, message)."""
    corps = json.dumps({"uid": uid, "lecteur": lecteur}).encode("utf-8")
    requete = urllib.request.Request(
        url.rstrip("/") + "/api/iot/rfid/scan", data=corps, method="POST",
        headers={"Content-Type": "application/json", "X-Device-Key": cle})
    for essai in range(3):
        try:
            with urllib.request.urlopen(requete, timeout=5) as rep:
                data = json.loads(rep.read().decode("utf-8"))
                return data.get("resultat", "ERREUR"), data.get("message", "")
        except urllib.error.HTTPError as e:
            try:
                detail = json.loads(e.read().decode("utf-8")).get("message", "")
            except Exception:
                detail = ""
            return "ERREUR", f"HTTP {e.code} {detail}".strip()
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            if essai == 2:
                return "ERREUR", f"Backend injoignable ({url}) : {e}"
            time.sleep(1)
    return "ERREUR", "Échec inconnu"


def afficher(uid, resultat, message):
    couleur = {"ARRIVEE": VERT, "DEPART": BLEU, "INCONNU": ROUGE, "ERREUR": ROUGE}.get(resultat, JAUNE)
    print(f"[{horodatage()}] {uid:<16} {couleur}{resultat:<13}{RESET} {message}", flush=True)


def normaliser(uid):
    return "".join(c for c in uid.upper() if c in "0123456789ABCDEF")


def trouver_port():
    """Cherche un Arduino (ou adaptateur CH340 / CP210x / FTDI) parmi les ports série."""
    from serial.tools import list_ports
    ports = list(list_ports.comports())
    mots = ("arduino", "ch340", "cp210", "ftdi", "usb-serial", "usb serial", "wch")
    candidats = [p for p in ports if any(m in f"{p.description} {p.manufacturer}".lower() for m in mots)]
    if not candidats and len(ports) == 1:
        candidats = ports
    if len(candidats) == 1:
        return candidats[0].device
    if not ports:
        print(f"{ROUGE}Aucun port série détecté : branchez l'Arduino en USB.{RESET}")
    else:
        print("Ports disponibles :")
        for p in ports:
            print(f"   {p.device:<8} {p.description}")
        print(f"{JAUNE}Indiquez le port : scripts\\pointage.cmd --port COM3 (ou RFID_PORT dans .env){RESET}")
    return None


def mode_serie(url, cle, lecteur, port, vitesse):
    try:
        import serial
    except ImportError:
        print(f"{ROUGE}Module pyserial absent : pip install pyserial{RESET}")
        sys.exit(1)

    while True:
        port_utilise = port or trouver_port()
        if not port_utilise:
            time.sleep(5)
            continue
        try:
            with serial.Serial(port_utilise, vitesse, timeout=1) as arduino:
                print(f"{VERT}Lecteur connecté sur {port_utilise}. Présentez un badge... (Ctrl+C pour arrêter){RESET}")
                while True:
                    ligne = arduino.readline().decode("ascii", errors="ignore").strip()
                    if not ligne.startswith("UID:"):
                        continue
                    uid = normaliser(ligne[4:])
                    if len(uid) < 4:
                        continue
                    resultat, message = envoyer(url, cle, uid, lecteur)
                    afficher(uid, resultat, message)
                    arduino.write(SIGNAUX.get(resultat, "E").encode("ascii"))
        except serial.SerialException as e:
            print(f"{ROUGE}[{horodatage()}] Lecteur déconnecté ({e}). Nouvel essai dans 3 s...{RESET}")
            time.sleep(3)


def mode_clavier(url, cle, lecteur):
    print(f"{VERT}Mode clavier : gardez cette fenêtre au premier plan et présentez un badge. (Ctrl+C pour arrêter){RESET}")
    for ligne in sys.stdin:
        uid = normaliser(ligne)
        if len(uid) >= 4:
            resultat, message = envoyer(url, cle, uid, lecteur)
            afficher(uid, resultat, message)


def main():
    env = lire_env()
    p = argparse.ArgumentParser(description="Pont RFID -> SmartDesk")
    p.add_argument("--port", default=env.get("RFID_PORT") or None, help="Port série de l'Arduino (ex : COM3)")
    p.add_argument("--vitesse", type=int, default=9600, help="Vitesse série (défaut 9600)")
    p.add_argument("--clavier", action="store_true", help="Lecteur USB de type clavier")
    p.add_argument("--test", metavar="UID", help="Envoie un seul passage puis s'arrête")
    p.add_argument("--url", default=env.get("BACKEND_URL") or "http://localhost:8095")
    p.add_argument("--lecteur", default=env.get("RFID_LECTEUR") or "ENTREE")
    args = p.parse_args()

    cle = env.get("RFID_DEVICE_KEY", "")
    if len(cle) < 16:
        print(f"{ROUGE}RFID_DEVICE_KEY absente ou trop courte dans .env (relancez scripts\\installer.cmd).{RESET}")
        sys.exit(1)

    print(f"{BLEU}=== SmartDesk - pointage RFID des professeurs ==={RESET}  backend : {args.url}")
    try:
        if args.test:
            uid = normaliser(args.test)
            resultat, message = envoyer(args.url, cle, uid, args.lecteur)
            afficher(uid, resultat, message)
            sys.exit(0 if resultat != "ERREUR" else 1)
        if args.clavier:
            mode_clavier(args.url, cle, args.lecteur)
        else:
            mode_serie(args.url, cle, args.lecteur, args.port, args.vitesse)
    except KeyboardInterrupt:
        print("\nArrêt du pointage.")


if __name__ == "__main__":
    main()
