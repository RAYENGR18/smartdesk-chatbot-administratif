/*
 * SmartDesk - lecteur de badges RFID pour le pointage des professeurs
 * Carte : Arduino Uno / Nano      Module : RC522 (MFRC522, 13,56 MHz)
 * Bibliothèque : "MFRC522" de GithubCommunity (miguelbalboa), via le gestionnaire de bibliothèques
 *
 * Câblage RC522 -> Arduino Uno       (ATTENTION : le RC522 s'alimente en 3,3 V, PAS en 5 V)
 *   SDA (SS) -> D10    SCK  -> D13    MOSI -> D11    MISO -> D12
 *   RST      -> D9     3.3V -> 3.3V   GND  -> GND    IRQ  -> non connecté
 * Signalisation (facultatif) :
 *   LED verte -> D5 (+ résistance 220 ohms)   LED rouge -> D6 (+ 220 ohms)   Buzzer actif -> D7
 *
 * Protocole avec le PC (USB, 9600 bauds) :
 *   Arduino -> PC : "UID:04A13B2C" à chaque badge présenté
 *   PC -> Arduino : 'A' arrivée, 'D' départ, 'X' déjà pointé, 'E' badge inconnu / erreur
 */
#include <SPI.h>
#include <MFRC522.h>

const byte SS_PIN = 10;
const byte RST_PIN = 9;
const byte LED_VERTE = 5;
const byte LED_ROUGE = 6;
const byte BUZZER = 7;
const unsigned long DELAI_MEME_BADGE_MS = 3000;  // ignore le même badge pendant 3 s

MFRC522 rfid(SS_PIN, RST_PIN);
String dernierUid = "";
unsigned long dernierPassage = 0;

void bip(unsigned int dureeMs) {
  digitalWrite(BUZZER, HIGH);
  delay(dureeMs);
  digitalWrite(BUZZER, LOW);
}

void signaler(char c) {
  switch (c) {
    case 'A':  // arrivée : vert + 1 bip
      digitalWrite(LED_VERTE, HIGH); bip(120); delay(800); digitalWrite(LED_VERTE, LOW);
      break;
    case 'D':  // départ : vert + 2 bips
      digitalWrite(LED_VERTE, HIGH); bip(100); delay(100); bip(100); delay(700); digitalWrite(LED_VERTE, LOW);
      break;
    case 'X':  // déjà pointé : vert et rouge brièvement
      digitalWrite(LED_VERTE, HIGH); digitalWrite(LED_ROUGE, HIGH); delay(500);
      digitalWrite(LED_VERTE, LOW); digitalWrite(LED_ROUGE, LOW);
      break;
    case 'E':  // inconnu ou erreur : rouge + bip long
      digitalWrite(LED_ROUGE, HIGH); bip(600); delay(900); digitalWrite(LED_ROUGE, LOW);
      break;
  }
}

void setup() {
  Serial.begin(9600);
  SPI.begin();
  rfid.PCD_Init();
  pinMode(LED_VERTE, OUTPUT);
  pinMode(LED_ROUGE, OUTPUT);
  pinMode(BUZZER, OUTPUT);
  digitalWrite(LED_VERTE, HIGH); delay(200); digitalWrite(LED_VERTE, LOW);
  Serial.println("READY");
}

void loop() {
  // Réponse du PC (résultat du pointage)
  while (Serial.available() > 0) {
    signaler((char) Serial.read());
  }

  if (!rfid.PICC_IsNewCardPresent() || !rfid.PICC_ReadCardSerial()) {
    return;
  }

  String uid = "";
  for (byte i = 0; i < rfid.uid.size; i++) {
    if (rfid.uid.uidByte[i] < 0x10) uid += "0";
    uid += String(rfid.uid.uidByte[i], HEX);
  }
  uid.toUpperCase();

  unsigned long maintenant = millis();
  if (!(uid == dernierUid && maintenant - dernierPassage < DELAI_MEME_BADGE_MS)) {
    dernierUid = uid;
    dernierPassage = maintenant;
    Serial.print("UID:");
    Serial.println(uid);
  }

  rfid.PICC_HaltA();
  rfid.PCD_StopCrypto1();
}
