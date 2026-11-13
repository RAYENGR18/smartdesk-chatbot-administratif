# ============================================
# SERVICE NLP COMPLET — FPST SmartDesk
# Faculty Assistant & Smart Technology Oracle
# Français + English + Arabe + Derja tunisienne
# ============================================

import spacy
import re
import os
import unicodedata
from langdetect import detect
from dotenv import load_dotenv

_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(_BASE_DIR, "..", ".env"))
load_dotenv(os.path.join(_BASE_DIR, ".env"))
_GROQ_API_KEY = os.getenv("GROQ_API_KEY")
# Lightweight, fast model used ONLY for classification (not generation)
_CLASSIFIER_MODEL = "llama-3.1-8b-instant"

# ============================================
# ALL VALID INTENT LABELS
# ============================================
_ALL_INTENTS = {
    "salutation", "au_revoir", "remerciement", "identite_bot", "vie_campus",
    "demande_attestation", "demande_attestation_travail",
    "demande_attestation_scolarite", "demande_attestation_residence",
    "demande_releve_notes", "demande_emploi_temps", "demande_salle",
    "demande_notes", "info_filiere", "demande_inscription", "demande_bourse",
    "demande_stage", "question_reglement", "demande_calendrier",
    "demande_reclamation", "probleme_technique", "demande_rendez_vous",
    "demande_conge", "demande_salaire", "demande_formation",
    "demande_remboursement", "demande_info", "continuation", "inconnu",
}

# Intents where simple keywords are reliable enough — skip LLM for speed
_TRIVIAL_INTENTS = {
    "salutation":   ["bonjour", "salut", "hello", "coucou", "bonsoir", "مرحبا", "سلام", "اهلا", "السلام"],
    "au_revoir":    ["au revoir", "bye", "goodbye", "à bientôt", "بالسلامة", "مع السلامة"],
    "remerciement": ["merci", "thank you", "thanks", "شكرا", "يعيشك", "بارك الله"],
}

# ============================================
# LLM INTENT CLASSIFIER PROMPT  (3 languages)
# ============================================
_CLASSIFIER_SYSTEM = """You are a multilingual intent classifier for FPST, a private university administrative chatbot.
You understand French, English, Arabic (MSA and Tunisian Darija).

Classify the user message into EXACTLY ONE of these intents:

salutation          – greeting (hi, bonjour, مرحبا)
au_revoir           – goodbye (bye, au revoir, بالسلامة)
remerciement        – thanks (merci, thank you, شكرا)
identite_bot        – what can you do? who are you?
vie_campus          – clubs, library, cafeteria, sport, WiFi, parking
demande_attestation_travail  – work certificate (for staff)
demande_attestation_scolarite – school enrollment certificate
demande_attestation_residence – residence / address certificate
demande_attestation – any other certificate or document (generic)
demande_releve_notes – official transcript / relevé de notes
demande_emploi_temps – timetable, class schedule, planning
demande_salle       – classroom / room availability
demande_notes       – grades, exam results, GPA
info_filiere        – programs, majors, bachelor/master degrees, courses
demande_inscription – registration, enrollment, tuition fees
demande_bourse      – scholarship, financial aid, tuition discount
demande_stage       – internship, PFE, work placement, stage
question_reglement  – rules, regulations, discipline, plagiarism, sanctions
demande_calendrier  – academic calendar, exam dates, holidays, deadlines
demande_reclamation – complaint, grievance
probleme_technique  – technical issue, bug, login problem
demande_rendez_vous – appointment, meeting request
demande_conge       – leave request, absence
demande_salaire     – salary, pay slip (staff only)
demande_formation   – training, workshops, professional development
demande_remboursement – refund, reimbursement
demande_info        – general information request
continuation        – short follow-up: yes, ok, more, sure, oui, نعم, details, and then?
inconnu             – none of the above

RULES:
- Output ONLY the intent label (one word, lowercase, underscores). Nothing else.
- Use the conversation history to resolve ambiguous follow-ups.
- If the user says "yes" / "ok" / "more" after a topic discussion → continuation.
- When uncertain between two intents, pick the more specific one.
- CRITICAL: "documents", "papers", "dossier", "what do I need" after a topic discussion
  → use the TOPIC intent (e.g. demande_stage), NOT demande_attestation.
  demande_attestation is ONLY for explicit requests like "I need an attestation de scolarité"."""

_CLASSIFIER_USER = """Conversation history (oldest → newest):
{history}

Current message: "{message}"
Intent:"""

def _fmt_history_short(history: list) -> str:
    """Compact history for classifier prompt (keep it short to save tokens)."""
    if not history:
        return "(none)"
    lines = []
    for t in history[-6:]:   # last 3 exchanges
        role = "User" if t.get("role") == "user" else "Bot"
        content = (t.get("content") or "").strip()[:200]
        lines.append(f"{role}: {content}")
    return "\n".join(lines)

def classify_intent_llm(message: str, language: str,
                        conversation_history: list = None) -> str | None:
    """Calls Groq (llama-3.1-8b-instant) to classify intent.
    Returns a valid intent string or None if the call fails."""
    if not _GROQ_API_KEY:
        return None
    try:
        from groq import Groq as _Groq
        client = _Groq(api_key=_GROQ_API_KEY, timeout=8)
        history_text = _fmt_history_short(conversation_history or [])
        user_prompt = _CLASSIFIER_USER.format(
            history=history_text, message=message)
        completion = client.chat.completions.create(
            model=_CLASSIFIER_MODEL,
            messages=[
                {"role": "system", "content": _CLASSIFIER_SYSTEM},
                {"role": "user",   "content": user_prompt},
            ],
            temperature=0.0,
            max_tokens=20,
        )
        raw = completion.choices[0].message.content.strip().lower()
        # Strip any accidental prefix/suffix the model might add
        raw = raw.split()[0] if raw else ""
        return raw if raw in _ALL_INTENTS else None
    except Exception as e:
        print(f"[WARN] LLM classifier failed: {e}")
        return None

def _trivial_keyword_intent(message_lower: str) -> str | None:
    """Fast keyword check for the simplest, highest-confidence intents only."""
    for intent, keywords in _TRIVIAL_INTENTS.items():
        for kw in keywords:
            if _keyword_matches(kw, message_lower):
                return intent
    return None

def _keyword_matches(keyword: str, text: str) -> bool:
    """Leading-boundary match: keyword must start at a word boundary but may have a suffix.
    This prevents 'hi' matching 'internship' while still letting 'document' match 'documents'.
    For multi-word phrases, plain substring match is fine."""
    if ' ' in keyword:
        return keyword in text
    # (?<!\w) = no word char immediately before → keyword starts a word
    return bool(re.search(r'(?<!\w)' + re.escape(keyword), text))

try:
    nlp_fr = spacy.load("fr_core_news_sm")
except OSError:
    # Modèle non installé : le service fonctionne quand même (sans extraction d'entités)
    # Installation : python -m spacy download fr_core_news_sm
    nlp_fr = None
    print("[WARN] Modele spaCy fr_core_news_sm absent : extraction d'entites desactivee.")

# ============================================
# TOUTES LES INTENTIONS
# ============================================
INTENTIONS = {

    "salutation": [
        "bonjour", "salut", "bonsoir", "hello", "bonne journée", "coucou", "hey",
        "hi", "good morning", "good afternoon", "good evening", "greetings",
        "مرحبا", "السلام عليكم", "أهلا", "صباح الخير", "مساء الخير",
        "اهلا", "سلام", "صباح النور", "مرحبة", "هاي", "عسلامة", "يعيشك"
    ],

    "demande_attestation": [
        # Only explicit certificate terms — NOT generic "document" or "paper"
        # which would falsely match contextual follow-ups like "what documents for that?"
        "attestation", "certificat",
        "certificate", "proof of enrollment",
        "شهادة", "تصريح",
        "نحب شهادة", "عطيني شهادة"
    ],

    "demande_attestation_travail": [
        "attestation de travail", "certificat travail", "attestation travail",
        "شهادة عمل", "وثيقة عمل",
        "شهادة خدمة", "ورقة خدمة", "نحب شهادة عمل"
    ],

    "demande_attestation_scolarite": [
        "attestation scolarité", "certificat scolarité", "attestation scolaire",
        "certificat de scolarité", "attestation d'inscription", "attestation inscription",
        "certificat inscription", "preuve inscription",
        "شهادة دراسة", "وثيقة تسجيل", "شهادة تمدرس",
        "شهادة باش", "ورقة مدرسة", "شهادة قراية", "وثيقة مالقراية"
    ],

    "demande_attestation_residence": [
        "attestation résidence", "certificat résidence", "justificatif domicile",
        "شهادة إقامة", "وثيقة سكن",
        "شهادة سكنى", "ورقة عنوان", "شهادة عنوان"
    ],

    "demande_releve_notes": [
        "relevé de notes", "releve de notes", "relevé notes", "bulletin de notes",
        "bulletin notes", "notes semestre", "résultats semestre", "mes notes",
        "كشف نقاط", "نتائج", "نقاط", "معدل",
        "نحب نقاطي", "قداش جبت", "شحال عندي", "وريني نتائجي"
    ],

    "demande_conge": [
        "congé", "vacances", "absence", "repos", "permission",
        "congé académique", "suspension", "césure",
        "إجازة", "عطلة", "غياب", "راحة",
        "كونجي", "نحب نروح", "نحب إجازة", "نحب راحة"
    ],

    "demande_salaire": [
        "salaire", "paie", "fiche de paie", "bulletin salaire", "rémunération",
        "راتب", "أجر", "مرتب", "كشف راتب",
        "خلاص", "فيش خلاص", "نحب خلاصي"
    ],

    "demande_reclamation": [
        "réclamation", "plainte", "problème", "signaler", "litige",
        "complaint", "signalement", "insatisfait",
        "شكوى", "تظلم", "مشكلة", "إشكال",
        "مشكل", "حكاية", "نحب نشكي", "عندي مشكلة"
    ],

    "demande_formation": [
        "formation", "stage", "apprentissage", "formation professionnelle",
        "formation continue", "atelier",
        "تدريب", "دورة", "تكوين", "تأهيل",
        "فورماسيون", "دورة تدريبية", "نحب نتكون"
    ],

    "demande_remboursement": [
        "remboursement", "rembourser", "frais", "remboursement frais",
        "paiement", "facture", "reçu",
        "استرداد", "تعويض", "مصاريف",
        "رجع فلوس", "نحب تعويض", "نحب فلوسي"
    ],

    "probleme_technique": [
        "problème technique", "bug", "erreur", "panne", "ne fonctionne pas",
        "ne marche pas", "bloqué", "crash", "freeze",
        "مشكلة تقنية", "خطأ", "عطل",
        "ما يخدمش", "مشكل تقني", "حاجة باطلة", "بلوكي"
    ],

    "demande_rendez_vous": [
        "rendez-vous", "réunion", "meeting", "appointment", "rencontre",
        "entretien", "audience", "rdv",
        "موعد", "اجتماع", "لقاء",
        "ميعاد", "نحب موعد", "نحب نلتقي"
    ],

    "demande_emploi_temps": [
        "emploi du temps", "emploi de temps", "planning", "horaire",
        "horaires", "calendrier", "programme", "cours aujourd'hui",
        "schedule", "timetable", "créneau", "créneaux",
        "quel cours", "quand est le cours", "à quelle heure",
        "my classes", "class schedule", "when is my class", "what time",
        "جدول", "جدول زمني", "مواعيد", "برنامج", "حصص",
        "وقتاش", "وقت الدراسة", "متى الحصة", "وقتاش نبدا",
        "شنية الحصص", "اليوم نقرا"
    ],

    "demande_salle": [
        "salle", "amphi", "amphithéâtre", "laboratoire", "labo",
        "salle disponible", "disponibilité salle", "réserver salle",
        "quelle salle", "où est la salle", "salle cours",
        "قاعة", "قاعات", "مخبر", "مدرج",
        "فين القاعة", "وين نقرا", "أنهي قاعة"
    ],

    "demande_notes": [
        "notes", "résultat", "résultats", "bulletin", "moyenne",
        "examen", "examens", "partiel", "session", "rattrapage",
        "grades", "results", "transcript", "gpa", "exam", "mark", "marks",
        "نتائج", "نقاط", "معدل", "امتحان",
        "نتيجة", "قداش جبت", "شحال المعدل"
    ],

    "info_filiere": [
        "filière", "spécialité", "département", "section",
        "programme d'études", "cursus", "licence", "master",
        "informatique", "gestion", "réseau", "cybersécurité",
        "matière", "coefficient", "module",
        "program", "major", "department", "degree", "bachelor",
        "computer science", "management", "networking", "cybersecurity",
        "subject", "course", "curriculum",
        "تخصص", "فرع", "قسم", "شعبة",
        "شنو نقرا", "تخصصي", "شنية المواد"
    ],

    "demande_inscription": [
        "inscription", "réinscription", "inscrire", "s'inscrire",
        "dossier inscription", "frais inscription", "frais scolarité",
        "coût", "prix", "tarif", "payer", "paiement scolarité",
        "registration", "enroll", "enrollment", "register", "tuition",
        "how to register", "registration fee", "apply",
        "تسجيل", "إعادة تسجيل", "ملف تسجيل",
        "نحب نتسجل", "كيفاش نتسجل", "قداش التسجيل"
    ],

    "demande_bourse": [
        "bourse", "aide financière", "réduction", "exonération",
        "bourse d'excellence", "bourse sociale", "aide",
        "scholarship", "financial aid", "grant", "tuition waiver", "discount",
        "منحة", "مساعدة مالية", "تخفيض",
        "نحب منحة", "كيفاش منحة", "شروط المنحة"
    ],

    "demande_stage": [
        "stage", "convention de stage", "offre de stage",
        "stage été", "stage pfe", "soutenance",
        "rapport de stage", "encadrant",
        "internship", "intern", "placement", "work placement",
        "interested in internship", "looking for internship",
        "تربص", "اتفاقية تربص", "عرض تربص",
        "نحب تربص", "وين نعمل تربص"
    ],

    "question_reglement": [
        "règlement", "reglement", "reglament", "interdit", "sanction", "discipline",
        "plagiat", "fraude", "exclusion", "triche",
        "règlement intérieur", "reglement interieur", "code de conduite",
        "absences autorisees", "absence autorisee", "tenue vestimentaire",
        "badge", "retard", "ponctualite", "conduite",
        "regulations", "rules", "internal rules", "code of conduct",
        "قانون", "عقوبة", "غش", "طرد",
        "شنو ممنوع", "قوانين الكلية", "النظام الداخلي", "لائحة"
    ],

    "demande_calendrier": [
        "calendrier", "date", "deadline", "date limite",
        "quand", "vacances", "jours fériés", "ferié",
        "rentrée", "fin d'année", "début cours",
        "رزنامة", "تاريخ", "متى", "وقتاش",
        "كان الدخول", "متى العطلة"
    ],

    "demande_info": [
        "information", "renseignement", "aide", "comment", "help",
        "c'est quoi", "qu'est-ce que", "pourquoi", "combien",
        "معلومات", "مساعدة", "كيف", "ما هو",
        "علاش", "كيفاش", "شنو", "قداش", "أشكون"
    ],

    "continuation": [
        # English
        "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "alright",
        "more", "more details", "tell me more", "go ahead", "continue",
        "please", "and then", "what else", "details", "elaborate",
        "expand", "go on", "keep going",
        # French
        "oui", "ouais", "bien sûr", "d'accord", "ok", "allez",
        "plus", "plus de détails", "dis-moi plus", "continue",
        "et ensuite", "quoi d'autre", "détails", "développe",
        # Arabic / Derja
        "نعم", "أيوا", "آه", "صح", "بالتأكيد",
        "أكثر", "أكمل", "تابع", "واصل", "بالتفصيل",
        "أيه", "واصل", "زيد"
    ],

    "remerciement": [
        "merci", "thank you", "je vous remercie", "parfait", "super",
        "excellent", "génial", "top", "bravo", "c'est bon",
        "thanks", "great", "awesome", "perfect", "wonderful",
        "شكرا", "شكراً جزيلاً", "أشكرك",
        "يعيشك", "بارك الله فيك", "شكرا برشا", "يسطرلك"
    ],

    "au_revoir": [
        "au revoir", "bye", "bonne journée", "à bientôt", "à demain",
        "à plus", "ciao", "bonne soirée", "bonne nuit",
        "goodbye", "see you", "take care", "good night", "see you later",
        "مع السلامة", "وداعا", "إلى اللقاء",
        "بالسلامة", "يسلمك", "نشوفك", "تصبح على خير"
    ],

    "vie_campus": [
        # French
        "club", "clubs", "association", "activité", "activités", "extra-scolaire",
        "vie étudiante", "vie campus", "vie universitaire",
        "bibliothèque", "biblio", "livre", "livres",
        "cafétéria", "cafeteria", "cafet", "restaurant",
        "sport", "sports", "salle de sport", "football", "basket",
        "hackathon", "coding", "codefpst", "entrepreneuriat",
        "wifi", "parking", "parking faculté",
        "club informatique", "club culture", "club sport",
        # English
        "campus life", "student life", "student club", "student clubs",
        "library", "books", "cafeteria", "canteen",
        "sport hall", "sports hall", "extracurricular",
        "wifi campus", "campus wifi", "campus parking",
        "student activities", "associations",
        # Arabic
        "نادي", "نوادي", "جمعية", "جمعيات", "أنشطة طلابية",
        "مكتبة", "كتب", "كافيتيريا", "مطعم",
        "رياضة", "ملعب", "واي فاي", "انترنت",
        "حياة الحرم الجامعي", "هاكاتون",
        "نادي الكود", "ناد", "الحياة الجامعية"
    ],

    "identite_bot": [
        # French
        "tu fais quoi", "que fais-tu", "que peux-tu faire", "tu peux faire quoi",
        "qui es-tu", "qui es tu", "qui êtes-vous", "tu es quoi",
        "c'est quoi fpst smartdesk", "c'est quoi fasto", "c'est qui",
        "tu es un robot", "tu es une ia", "tu es intelligent",
        "présente-toi", "presente toi", "parle-moi de toi",
        "tes capacités", "tes fonctionnalités", "ce que tu sais faire",
        # English
        "what do you do", "what can you do", "what are you",
        "who are you", "what is your purpose", "tell me about yourself",
        "what are your capabilities", "how can you help", "how can you help me",
        "what kind of assistant", "are you a bot", "are you ai",
        "are you an ai", "are you intelligent", "introduce yourself",
        "what do u do", "what r u", "who r u", "whos this",
        "what is fpst smartdesk", "what is this chatbot",
        # Arabic
        "شنو تعمل", "شنو تقدر تعمل", "أشكون أنت", "من أنت",
        "اشكون أنت", "أنت روبو", "أنت ذكاء اصطناعي",
        "عرّف بنفسك", "عرف بنفسك", "قداش تقدر تعمل",
        "ماذا تفعل", "ما هي قدراتك", "ماذا تعرف"
    ]
}

# ============================================
# RÉPONSES BILINGUES — PERSONNALITÉ FPST SmartDesk
# ============================================
REPONSES = {
    "fr": {
        "salutation": "Bonjour ! 👋 Je suis **FPST SmartDesk**, votre assistant IA de la Faculté FPST. Je suis là pour vous aider 24h/24 ! 💡\n\nVoici ce que je peux faire pour vous :\n📄 Générer des documents administratifs\n📅 Consulter votre emploi du temps\n🎓 Informations sur les filières\n📝 Aide à l'inscription\n\nComment puis-je vous aider ?",

        "demande_attestation": "📄 Je peux vous générer une attestation ! Quel type souhaitez-vous ?\n\n• **Attestation de scolarité** — Prouve que vous êtes inscrit\n• **Attestation de travail** — Pour le personnel\n• **Attestation de résidence** — Certificat de domicile\n\nDites-moi le type et je génère le document automatiquement ✨",

        "demande_attestation_travail": "📄 Parfait ! Je vais générer votre **attestation de travail**.\n\n▶️ Veuillez remplir le formulaire qui apparaît.\n\nLe document sera disponible en **PDF et Word** pour téléchargement immédiat.",

        "demande_attestation_scolarite": "📄 Parfait ! Je vais générer votre **attestation de scolarité**.\n\n▶️ Veuillez remplir le formulaire avec vos informations.\n\nLe document sera pré-rempli avec votre profil et prêt en quelques secondes ⚡",

        "demande_attestation_residence": "📄 Je vais générer votre **attestation de résidence**.\n\n▶️ Veuillez remplir le formulaire.\n\nVous aurez le choix entre le format PDF et Word.",

        "demande_releve_notes": "📊 Pour obtenir votre **relevé de notes** :\n\n• **Relevé provisoire** → Disponible en ligne sur elearning.fpst.tn après la publication des résultats\n• **Relevé officiel (tamponné)** → Au secrétariat (Bât. A, RDC) — Délai 48h\n\n📧 Contact : secretariat@fpst.tn\n\nVoulez-vous que je vous aide avec autre chose ?",

        "demande_conge": "🗓️ Je peux traiter votre demande de congé.\n\n📋 **Rappel de la politique** :\n• Congé annuel : 30 jours/an (personnel)\n• Congé académique (césure) : max 1 an (étudiants)\n• Toute absence doit être justifiée sous 72h\n\nQuelle est la date de début et de fin souhaitée ?",

        "demande_salaire": "💰 Je peux vous fournir votre **fiche de paie**.\n\nPour quel mois souhaitez-vous la fiche ?\n\n📧 Contact RH : rh@fpst.tn | Bureau 201, Bât. A",

        "demande_reclamation": "📢 Je prends en charge votre **réclamation**.\n\n📋 **Procédure** :\n1. Décrivez votre problème en détail\n2. Accusé de réception sous 24h\n3. Traitement sous 5 jours ouvrables\n4. Réponse officielle sous 10 jours\n\n📧 Ou par email : reclamation@fpst.tn\n📍 Bureau 110, Bât. A\n\nPouvez-vous décrire votre problème ?",

        "demande_formation": "🎓 Nous proposons plusieurs formations !\n\n**Licences (3 ans)** : Informatique, Génie Logiciel, Réseaux, Gestion, Marketing\n**Masters (2 ans)** : IA & Data Science, Cybersécurité, MSI, Finance\n\nQuel domaine vous intéresse ? Je peux vous donner les détails du programme 📚",

        "demande_remboursement": "💳 **Politique de remboursement** :\n\n• Avant le 31 Octobre → Remboursement de 70%\n• Avant le 31 Décembre → Remboursement de 30%\n• Après le 31 Décembre → Aucun remboursement\n\n📋 Procédure : Demande écrite + reçu original au service financier\n📧 Contact : finance@fpst.tn | Bureau 103",

        "probleme_technique": "🔧 Je suis désolé pour ce problème technique !\n\n**Solutions rapides** :\n• WiFi (FPST-WiFi) → Votre ID étudiant + mot de passe d'inscription\n• Plateforme e-learning → elearning.fpst.tn\n• Problème de compte → Réinitialiser via support@fpst.tn\n\n📍 Support IT : Bureau 304, Bât. A (Lun-Ven 09h-17h)\n📧 Email : support@fpst.tn\n\nPouvez-vous décrire le problème en détail ?",

        "demande_rendez_vous": "📅 Je peux vous fixer un rendez-vous !\n\nAvec quel service souhaitez-vous un RDV ?\n• 📋 Secrétariat → secretariat@fpst.tn\n• 💼 Service inscription → inscription@fpst.tn\n• 🎓 Bureau des stages → stages@fpst.tn\n• 💰 Service financier → finance@fpst.tn\n\nQuelle date vous convient ?",

        "demande_emploi_temps": "📅 Votre emploi du temps est disponible !\n\n▶️ **Consultez la page Planning** de cette application pour voir votre programme complet.\n\nSélectionnez votre filière pour afficher la grille semaine avec tous vos cours, salles et professeurs.\n\n🔍 Les créneaux sont colorés par type :\n🔵 CM (Cours Magistral) | 🟢 TD (Travaux Dirigés) | 🟠 TP (Travaux Pratiques)\n\nBesoin d'autre chose ?",

        "demande_salle": "🏫 Pour la **disponibilité des salles** :\n\n▶️ Consultez la page **Gestion des salles** (Admin) ou dites-moi le jour et l'heure souhaités.\n\n📍 Nos principales salles :\n• Amphi A101 (200 places) • Amphi A102 (120 places)\n• Salles TD A103-A106 (40 places)\n• Salles info A301-A302 (30 postes)\n• Lab Réseaux A303 • Lab IA A304\n\nQuelle salle cherchez-vous ?",

        "demande_notes": "📊 Pour consulter vos **résultats et notes** :\n\n1. **En ligne** → elearning.fpst.tn (après publication)\n2. **Au secrétariat** → Bât. A, RDC (relevé officiel)\n\n📋 **Rappel evaluation** :\n• Contrôle continu : 40% | Partiel : 20% | Final : 40%\n• Note minimum validation : 10/20\n• Rattrapage disponible pour les modules < 10\n\n📧 Service examens : examens@fpst.tn | Bureau 105",

        "info_filiere": "🎓 Notre faculté propose **6 Licences** et **4 Masters** !\n\n**LICENCES (3 ans)** :\n• 🖥️ Informatique de Gestion (IG)\n• 🔧 Génie Logiciel (GL)\n• 🌐 Réseaux & Télécom (RT)\n• 📊 Sciences de Gestion (SG)\n• 📱 Marketing Digital (MD)\n• 💻 Systèmes d'Information (SI)\n\n**MASTERS (2 ans)** :\n• 🤖 IA & Data Science (IADS)\n• 🔒 Cybersécurité (CS)\n• 📈 Management SI (MSI)\n• 💼 Finance & Comptabilité (FC)\n\nQuelle filière vous intéresse ? Je peux vous donner le programme détaillé !",

        "demande_inscription": "📝 **Guide d'inscription FPST** :\n\n**Étapes** :\n1. 📋 Pré-inscription en ligne sur inscription.fpst.tn\n2. 📂 Préparer le dossier (CIN, bac, photos, relevés)\n3. 💳 Payer les frais (Licence: 4500 DT/an | Master: 5500 DT/an)\n4. 🎫 Retirer carte étudiante au Bureau 101\n\n**Dates limites** :\n• L1 : 01 Juillet — 30 Septembre\n• Réinscription : 01-30 Septembre\n• Master : 01-15 Octobre\n\n📧 Contact : inscription@fpst.tn | Bureau 101",

        "demande_bourse": "🏅 **Bourses disponibles** :\n\n**Bourse d'excellence** :\n• Major de promo → Exonération 100%\n• 2ème-3ème → Réduction 50%\n• Moyenne ≥ 15/20 → Réduction 25%\n\n**Bourse sociale** :\n• Réduction 20-50% selon le revenu familial\n• Dossier à déposer avant le 15 Octobre\n\n**Réduction** : -10% si paiement intégral à l'inscription\n\n📧 Contact : finance@fpst.tn | Bureau 103",

        "demande_stage": "💼 **Tout sur les stages** :\n\n📋 **Stages obligatoires** :\n• L2 : Stage d'observation (1 mois, été)\n• L3 : PFE (2 mois)\n• M2 : Stage pro (4-6 mois)\n\n📍 Bureau des stages : Bureau 203 | stages@fpst.tn\n🌐 Offres en ligne : stages.fpst.tn\n\n⚠️ La convention de stage doit être déposée 15 jours avant le début !\n\nVoulez-vous plus de détails sur la procédure ?",

        "question_reglement": "📜 **Points clés du règlement** :\n\n✅ Présence obligatoire (max 25% d'absences/matière)\n✅ Badge étudiant obligatoire\n❌ Fraude = zéro au module + avertissement\n❌ Plagiat max accepté : 20% de similitude\n❌ Téléphone interdit en cours\n\n**Sanctions** : Avertissement → Exclusion temporaire → Exclusion définitive\n\nVoulez-vous le règlement complet ou un point spécifique ?",

        "demande_calendrier": "📅 **Calendrier académique 2024-2025** :\n\n🔵 **Semestre 1** : 15 Sept — 18 Janvier\n🟢 **Semestre 2** : 10 Février — 14 Juin\n\n🎄 Vacances hiver : 22 Déc — 5 Jan\n🌸 Vacances printemps : 22-29 Mars\n☀️ Vacances été : Juillet-Août\n\n📝 Dates clés :\n• Examens S1 : 6-18 Janvier\n• Examens S2 : 2-14 Juin\n• Soutenances PFE : 1-15 Juin\n• Remise diplômes : 5 Octobre",

        "demande_info": "💡 Je suis **FPST SmartDesk**, votre assistant IA ! Posez-moi votre question !\n\nJe peux vous aider avec :\n📄 Documents | 📅 Planning | 🎓 Filières | 📝 Inscription\n💰 Bourses | 💼 Stages | 📜 Règlement | 🔧 Support\n\nDites-moi de quoi avez-vous besoin ! 😊",

        "remerciement": "Avec plaisir ! 😊 Je suis là pour vous 24h/24 !\n\nN'hésitez pas à revenir si vous avez d'autres questions. Bonne continuation ! 🎓✨",

        "au_revoir": "Au revoir ! 👋 Bonne continuation dans vos études !\n\n📞 En cas de besoin urgent :\n📧 secretariat@fpst.tn | ☎️ +216 71 000 000\n\nÀ bientôt sur FPST SmartDesk ! 🤖✨",

        "vie_campus": "🏫 **Vie sur le campus FPST** :\n\n🎓 **Clubs étudiants** :\n• 💻 **CodeFPST** — Club informatique & coding\n• 🚀 **Club Entrepreneuriat & Innovation**\n• 🎭 **Club Culture & Arts**\n• ⚽ **Club Sports**\n• 🏆 Hackathons et portes ouvertes réguliers\n\n📚 **Bibliothèque** : 10 000+ références | Lun-Ven 08h-19h\n🍽️ **Cafétéria** : Bât. B, RDC | 08h-17h\n🏋️ **Salle de sport** : Mar & Jeu 17h-20h (étudiants inscrits)\n📶 **WiFi** : Couverture totale du campus (identifiants au Bureau IT 304)\n🚗 **Parking** : Gratuit pour les étudiants | 07h30-20h\n\n💬 Tu veux t'inscrire à un club ? Contacte le Bureau de la Vie Étudiante : **vie.etudiante@fpst.tn** 😊",

        "identite_bot": "🤖 Je suis **FPST SmartDesk**, l'assistant IA officiel de la Faculté Privée des Sciences et Technologies (FPST) !\n\n🎯 **Ce que je sais faire :**\n📄 Générer des documents administratifs (attestations, relevés)\n📅 Consulter et gérer les emplois du temps\n🎓 Informer sur les filières, programmes et matières\n📝 Guider les démarches d'inscription et de réinscription\n💰 Renseigner sur les bourses et aides financières\n💼 Aider avec les stages et le PFE\n📜 Expliquer le règlement intérieur\n🔧 Résoudre les problèmes techniques\n\n💬 Posez-moi n'importe quelle question sur la faculté — je suis là 24h/24 ! 😊",

        "inconnu": "🤔 Je n'ai pas bien compris votre demande, mais pas de panique !\n\nEssayez l'une de ces formulations :\n• \"Attestation de scolarité\" pour un document\n• \"Emploi du temps\" pour votre planning\n• \"Filières\" pour les programmes\n• \"Inscription\" pour les démarches\n\nOu posez simplement votre question et je ferai de mon mieux ! 💪",

        "continuation": "💬 Pouvez-vous préciser un peu plus ce dont vous avez besoin ? Je veux m'assurer de vous donner la bonne information ! 😊"
    },
    "ar": {
        "salutation": "مرحبا ! 👋 أنا **فاستو (FPST SmartDesk)**، المساعد الذكي متاع كلية FPST.\n\nنقدر نعاونك في :\n📄 الوثائق الإدارية\n📅 جدول الحصص\n🎓 معلومات التخصصات\n📝 التسجيل\n\nقلّي شنو تحب ؟ 😊",

        "demande_attestation": "📄 نقدر نعملك شهادة ! شنو تحب بالضبط ؟\n\n• **شهادة دراسة** — تثبت إنك مسجل\n• **شهادة عمل** — لزمت عمل\n• **شهادة إقامة** — وثيقة عنوان\n\nقلي النوع ونعملها تو ✨",

        "demande_attestation_travail": "📄 باهي ! تو نعملك **شهادة العمل**.\n\n▶️ عبّي الفورميلار اللي يظهر.\n\nالوثيقة تكون جاهزة بالـ PDF و Word ⚡",

        "demande_attestation_scolarite": "📄 باهي ! تو نعملك **شهادة الدراسة**.\n\n▶️ عبّي معلوماتك في الفورميلار.\n\nالوثيقة تتعبّا أوتوماتيك من البروفيل متاعك ⚡",

        "demande_attestation_residence": "📄 تو نعملك **شهادة الإقامة**.\n\n▶️ عبّي الفورميلار.\n\nتنجم تختار PDF ولا Word.",

        "demande_releve_notes": "📊 باش تشوف **كشف النقاط** :\n\n• كشف مؤقت → elearning.fpst.tn\n• كشف رسمي (مختوم) → الأمانة العامة (بات أ)\n\nالمهلة : 48 ساعة\n📧 secretariat@fpst.tn\n\nفمّا شيء آخر ؟",

        "demande_conge": "🗓️ نقدر نعالج طلب الإجازة متاعك.\n\n📋 **تفكير** :\n• إجازة سنوية : 30 يوم/سنة (للموظفين)\n• إجازة أكاديمية : أقصاها سنة (للطلبة)\n• كل غياب لازم يتبرر في 72 ساعة\n\nشنو التواريخ ؟",

        "demande_salaire": "💰 نقدر نعطيك **كشف الراتب**.\n\nعن أنهي شهر ؟\n\n📧 rh@fpst.tn | مكتب 201",

        "demande_reclamation": "📢 نسجّل **الشكوى** متاعك.\n\n📋 الإجراءات :\n1. اوصف المشكلة\n2. إشعار في 24 ساعة\n3. معالجة في 5 أيام\n4. رد رسمي في 10 أيام\n\n📧 reclamation@fpst.tn\n\nقلي شنو المشكلة ؟",

        "demande_formation": "🎓 عندنا عدة تخصصات !\n\n**ليسانس** : معلوماتية، شبكات، تصرف، تسويق\n**ماستر** : ذكاء اصطناعي، أمن سيبراني، MSI\n\nأنهي مجال يهمك ؟ 📚",

        "demande_remboursement": "💳 **سياسة الاسترداد** :\n\n• قبل 31 أكتوبر → استرداد 70%\n• قبل 31 ديسمبر → استرداد 30%\n• بعد 31 ديسمبر → لا استرداد\n\n📧 finance@fpst.tn | مكتب 103",

        "probleme_technique": "🔧 آسف على المشكل التقني !\n\n📍 الدعم : مكتب 304 (الاثنين-الجمعة 09h-17h)\n📧 support@fpst.tn\n\nاوصفلي المشكلة تو نعاونك 🛠️",

        "demande_rendez_vous": "📅 نقدر نحجزلك موعد !\n\nمع أنهي مصلحة ؟\n• 📋 الأمانة\n• 💼 التسجيل\n• 🎓 التربصات\n• 💰 المحاسبة\n\nأنهي تاريخ يناسبك ؟",

        "demande_emploi_temps": "📅 جدول الحصص متاعك موجود !\n\n▶️ روح لصفحة **البرنامج** في التطبيق واختار تخصصك.\n\n🔵 محاضرة | 🟢 أعمال موجهة | 🟠 أعمال تطبيقية\n\nتحب حاجة أخرى ؟",

        "demande_salle": "🏫 باش تعرف **توفر القاعات** :\n\n▶️ صفحة إدارة القاعات أو قلي اليوم والوقت.\n\n📍 قاعاتنا الرئيسية :\n• مدرج A101 (200 بلاصة)\n• قاعات TD (40 بلاصة)\n• مخابر معلوماتية A301-A304\n\nأنهي قاعة تحب ؟",

        "demande_notes": "📊 باش تشوف **النتائج** :\n\n1. أونلاين → elearning.fpst.tn\n2. الأمانة العامة → بات أ\n\n📋 التقييم : مراقبة مستمرة 40% | جزئي 20% | نهائي 40%\n\n📧 examens@fpst.tn | مكتب 105",

        "info_filiere": "🎓 عندنا **6 ليسانس** و **4 ماستر** !\n\n🖥️ معلوماتية التصرف\n🔧 هندسة البرمجيات\n🌐 شبكات واتصالات\n📊 علوم التصرف\n🤖 ذكاء اصطناعي (ماستر)\n🔒 أمن سيبراني (ماستر)\n\nأنهي تخصص يهمك ؟ نعطيك التفاصيل ! 📚",

        "demande_inscription": "📝 **دليل التسجيل FPST** :\n\n1. 📋 تسجيل أولي : inscription.fpst.tn\n2. 📂 جهّز الملف (CIN، باك، صور، كشف نقاط)\n3. 💳 خلّص (ليسانس: 4500 د/سنة | ماستر: 5500 د)\n4. 🎫 اقبض الكارت من مكتب 101\n\n📧 inscription@fpst.tn",

        "demande_bourse": "🏅 **المنح المتوفرة** :\n\n• الأول في الدفعة → إعفاء 100%\n• معدل ≥ 15/20 → تخفيض 25%\n• منحة اجتماعية → تخفيض 20-50%\n\n📧 finance@fpst.tn | مكتب 103",

        "demande_stage": "💼 **التربصات** :\n\n• L2 : تربص ملاحظة (شهر)\n• L3 : مشروع تخرج (شهرين)\n• M2 : تربص مهني (4-6 أشهر)\n\n📍 مكتب 203 | stages@fpst.tn\n🌐 stages.fpst.tn",

        "question_reglement": "📜 **القانون الداخلي** :\n\n✅ الحضور إلزامي\n❌ الغش = صفر + تحذير\n❌ الانتحال العلمي ممنوع\n📱 الهاتف ممنوع في الحصص\n\nتحب تفاصيل أكثر ؟",

        "demande_calendrier": "📅 **الرزنامة 2024-2025** :\n\n🔵 S1 : 15 سبتمبر — 18 جانفي\n🟢 S2 : 10 فيفري — 14 جوان\n\n🎄 عطلة الشتاء : 22 ديسمبر — 5 جانفي\n☀️ عطلة الصيف : جويلية-أوت",

        "demande_info": "💡 أنا **فاستو**، المساعد الذكي ! اسألني !\n\n📄 وثائق | 📅 برنامج | 🎓 تخصصات | 📝 تسجيل\n💰 منح | 💼 تربصات | 📜 قانون | 🔧 دعم\n\nقلي شنو تحب ! 😊",

        "remerciement": "العفو ! 😊 أنا هنا باش نعاونك ديما !\n\nإذا عندك أسئلة أخرى ارجع ديركت. بالتوفيق ! 🎓✨",

        "au_revoir": "بالسلامة ! 👋 بالتوفيق في القراية !\n\n📧 secretariat@fpst.tn | ☎️ +216 71 000 000\n\nنشوفك مرة أخرى ! 🤖✨",

        "vie_campus": "🏫 **الحياة في حرم FPST** :\n\n🎓 **النوادي الطلابية** :\n• 💻 **CodeFPST** — نادي المعلوماتية والبرمجة\n• 🚀 **نادي ريادة الأعمال والابتكار**\n• 🎭 **نادي الثقافة والفنون**\n• ⚽ **النادي الرياضي**\n• 🏆 هاكاتونات وأيام مفتوحة منتظمة\n\n📚 **المكتبة** : أكثر من 10,000 مرجع | الاثنين-الجمعة 08h-19h\n🍽️ **الكافيتيريا** : بناية ب، الطابق الأرضي | 08h-17h\n🏋️ **القاعة الرياضية** : الثلاثاء والخميس 17h-20h\n📶 **WiFi** : تغطية كاملة للحرم الجامعي\n🚗 **مواقف السيارات** : مجانية للطلبة | 07h30-20h\n\n💬 تحب تنضم لنادي؟ تواصل مع مكتب الحياة الطلابية: **vie.etudiante@fpst.tn** 😊",

        "identite_bot": "🤖 أنا **FPST SmartDesk**، المساعد الذكي الرسمي لكلية العلوم والتكنولوجيا الخاصة (FPST) !\n\n🎯 **شنو نقدر نعمل :**\n📄 نعمل وثائق إدارية (شهادات، كشوف نقاط)\n📅 نعرضلك جدول الحصص والبرنامج\n🎓 نعطيك معلومات على التخصصات والمواد\n📝 نرشدك في إجراءات التسجيل\n💰 نعلمك على المنح والمساعدات المالية\n💼 نساعدك في التربصات ومشروع التخرج\n📜 نشرحلك القانون الداخلي\n🔧 نحل معاك المشاكل التقنية\n\n💬 اسألني أي سؤال على الكلية — أنا هنا 24/24 ! 😊",

        "inconnu": "🤔 ما فهمتش مليح، أما ما تخافش !\n\nجرّب تقول :\n• \"شهادة\" باش نعملك وثيقة\n• \"جدول\" باش تشوف الحصص\n• \"تخصص\" باش تعرف البرامج\n• \"تسجيل\" باش تعرف الإجراءات\n\nأو اطرح سؤالك ونحاول نجاوبك ! 💪",

        "continuation": "💬 قلّي أكثر شنو تحب بالضبط ؟ حتى نعطيك الجواب الصح ! 😊"
    },

    # ═══════════════════════════════════════════════
    # ENGLISH
    # ═══════════════════════════════════════════════
    "en": {
        "salutation": "Hello! 👋 I'm **FPST SmartDesk**, the AI assistant for FPST Faculty. I'm here to help you 24/7! 💡\n\nHere's what I can do:\n📄 Generate administrative documents\n📅 Check your timetable\n🎓 Program information\n📝 Registration help\n\nHow can I assist you?",

        "demande_attestation": "📄 I can generate a certificate for you! Which type do you need?\n\n• **School certificate** — Proves your enrollment\n• **Work certificate** — For staff\n• **Residence certificate** — Address proof\n\nTell me the type and I'll generate it instantly ✨",

        "demande_attestation_travail": "📄 Sure! I'll generate your **work certificate**.\n\n▶️ Please fill in the form that appears.\n\nThe document will be available in **PDF and Word** for immediate download.",

        "demande_attestation_scolarite": "📄 Sure! I'll generate your **school enrollment certificate**.\n\n▶️ Please fill in the form with your information.\n\nThe document will be pre-filled with your profile data and ready in seconds ⚡",

        "demande_attestation_residence": "📄 I'll generate your **residence certificate**.\n\n▶️ Please fill in the form.\n\nYou can choose between PDF and Word format.",

        "demande_releve_notes": "📊 To get your **transcript**:\n\n• **Provisional transcript** → Available online at elearning.fpst.tn after results are published\n• **Official transcript (stamped)** → Secretary's office (Bldg. A, Ground Floor) — 48h processing\n\n📧 Contact: secretariat@fpst.tn\n\nCan I help with anything else?",

        "demande_conge": "🗓️ I can process your leave request.\n\n📋 **Policy reminder**:\n• Annual leave: 30 days/year (staff)\n• Academic leave: max 1 year (students)\n• All absences must be justified within 72h\n\nWhat are the start and end dates?",

        "demande_salaire": "💰 I can provide your **pay slip**.\n\nFor which month would you like it?\n\n📧 Contact HR: rh@fpst.tn | Office 201, Bldg. A",

        "demande_reclamation": "📢 I'll register your **complaint**.\n\n📋 **Process**:\n1. Describe your issue in detail\n2. Acknowledgment within 24h\n3. Processing within 5 business days\n4. Official response within 10 days\n\n📧 Or by email: reclamation@fpst.tn\n📍 Office 110, Bldg. A\n\nCan you describe the issue?",

        "demande_formation": "🎓 We offer several programs!\n\n**Bachelor's (3 years)**: Computer Science, Software Engineering, Networks, Management, Marketing\n**Master's (2 years)**: AI & Data Science, Cybersecurity, MSI, Finance\n\nWhich field interests you? I can give you program details 📚",

        "demande_remboursement": "💳 **Refund Policy**:\n\n• Before October 31 → 70% refund\n• Before December 31 → 30% refund\n• After December 31 → No refund\n\n📋 Procedure: Written request + original receipt to the finance department\n📧 Contact: finance@fpst.tn | Office 103",

        "probleme_technique": "🔧 Sorry about the technical issue!\n\n**Quick solutions**:\n• WiFi (FPST-WiFi) → Your student ID + enrollment password\n• E-learning platform → elearning.fpst.tn\n• Account issue → Reset via support@fpst.tn\n\n📍 IT Support: Office 304, Bldg. A (Mon-Fri 9am-5pm)\n📧 Email: support@fpst.tn\n\nCan you describe the issue in detail?",

        "demande_rendez_vous": "📅 I can schedule an appointment for you!\n\nWith which department would you like to meet?\n• 📋 Secretary's office → secretariat@fpst.tn\n• 💼 Registration → inscription@fpst.tn\n• 🎓 Internship office → stages@fpst.tn\n• 💰 Finance → finance@fpst.tn\n\nWhat date works for you?",

        "demande_emploi_temps": "📅 Your timetable is available!\n\n▶️ **Check the Planning page** in this application to see your complete schedule.\n\nSelect your program to display the weekly grid with all your classes, rooms, and professors.\n\n🔵 Lecture | 🟢 Tutorial | 🟠 Lab\n\nNeed anything else?",

        "demande_salle": "🏫 For **room availability**:\n\n▶️ Check the Room Management page (Admin) or tell me the day and time.\n\n📍 Main rooms:\n• Amphitheater A101 (200 seats) • Amphitheater A102 (120 seats)\n• TD Rooms A103-A106 (40 seats)\n• Computer Labs A301-A302 (30 workstations)\n• Network Lab A303 • AI Lab A304\n\nWhich room are you looking for?",

        "demande_notes": "📊 To check your **grades and results**:\n\n1. **Online** → elearning.fpst.tn (after publication)\n2. **Secretary's office** → Bldg. A, Ground Floor (official transcript)\n\n📋 **Assessment breakdown**:\n• Continuous assessment: 40% | Midterm: 20% | Final: 40%\n• Minimum passing grade: 10/20\n• Retake available for grades below 10\n\n📧 Exam service: examens@fpst.tn | Office 105",

        "info_filiere": "🎓 Our faculty offers **6 Bachelor's** and **4 Master's** programs!\n\n**BACHELOR'S (3 years)**:\n• 🖥️ Computer Science (CS)\n• 🔧 Software Engineering (SE)\n• 🌐 Networks & Telecom (NT)\n• 📊 Management Sciences (MS)\n• 📱 Digital Marketing (DM)\n• 💻 Information Systems (IS)\n\n**MASTER'S (2 years)**:\n• 🤖 AI & Data Science (IADS)\n• 🔒 Cybersecurity (CS)\n• 📈 Management IS (MSI)\n• 💼 Finance & Accounting (FA)\n\nWhich program interests you? I can give you the detailed curriculum!",

        "demande_inscription": "📝 **FPST Registration Guide**:\n\n**Steps**:\n1. 📋 Pre-register online at inscription.fpst.tn\n2. 📂 Prepare your file (ID, diploma, photos, transcripts)\n3. 💳 Pay tuition (Bachelor: 4500 DT/year | Master: 5500 DT/year)\n4. 🎫 Collect your student card at Office 101\n\n**Deadlines**:\n• L1: July 1 — September 30\n• Re-enrollment: September 1-30\n• Master: October 1-15\n\n📧 Contact: inscription@fpst.tn | Office 101",

        "demande_bourse": "🏅 **Available Scholarships**:\n\n**Excellence Scholarship**:\n• Top of class → 100% tuition waiver\n• 2nd-3rd → 50% discount\n• Average ≥ 15/20 → 25% discount\n\n**Social Scholarship**:\n• 20-50% discount based on family income\n• Application deadline: October 15\n\n**Discount**: -10% if full payment at registration\n\n📧 Contact: finance@fpst.tn | Office 103",

        "demande_stage": "💼 **All about internships**:\n\n📋 **Required internships**:\n• L2: Observation internship (1 month, summer)\n• L3: Final project (2 months)\n• M2: Professional internship (4-6 months)\n\n📍 Internship office: Office 203 | stages@fpst.tn\n🌐 Job board: stages.fpst.tn\n\n⚠️ The internship agreement must be submitted 15 days before the start!\n\nWould you like more details?",

        "question_reglement": "📜 **Key rules**:\n\n✅ Attendance is mandatory (max 25% absences per subject)\n✅ Student ID badge required\n❌ Cheating = zero for the module + warning\n❌ Max plagiarism: 20% similarity\n❌ Phone use prohibited in class\n\n**Sanctions**: Warning → Temporary suspension → Permanent expulsion\n\nWould you like the full regulations or a specific point?",

        "demande_calendrier": "📅 **Academic Calendar 2024-2025**:\n\n🔵 **Semester 1**: Sept 15 — Jan 18\n🟢 **Semester 2**: Feb 10 — Jun 14\n\n🎄 Winter break: Dec 22 — Jan 5\n🌸 Spring break: Mar 22-29\n☀️ Summer break: July-August\n\n📝 Key dates:\n• S1 Exams: Jan 6-18\n• S2 Exams: Jun 2-14\n• PFE Defenses: Jun 1-15\n• Graduation: Oct 5",

        "demande_info": "💡 I'm **FPST SmartDesk**, your AI assistant! Ask me anything!\n\nI can help with:\n📄 Documents | 📅 Schedule | 🎓 Programs | 📝 Registration\n💰 Scholarships | 💼 Internships | 📜 Rules | 🔧 Support\n\nWhat do you need? 😊",

        "remerciement": "You're welcome! 😊 I'm here for you 24/7!\n\nDon't hesitate to come back if you have more questions. Good luck! 🎓✨",

        "au_revoir": "Goodbye! 👋 Best of luck with your studies!\n\n📞 In case of emergency:\n📧 secretariat@fpst.tn | ☎️ +216 71 000 000\n\nSee you soon on FPST SmartDesk! 🤖✨",

        "vie_campus": "🏫 **Campus Life at FPST** :\n\n🎓 **Student Clubs** :\n• 💻 **CodeFPST** — Coding & Computer Science Club\n• 🚀 **Entrepreneurship & Innovation Club**\n• 🎭 **Culture & Arts Club**\n• ⚽ **Sports Club**\n• 🏆 Regular Hackathons & Open Days\n\n📚 **Library** : 10,000+ references | Mon-Fri 08:00-19:00\n🍽️ **Cafeteria** : Building B, Ground Floor | 08:00-17:00\n🏋️ **Sports Hall** : Tuesdays & Thursdays 17:00-20:00 (registered students)\n📶 **WiFi** : Full campus coverage (credentials at IT Office 304)\n🚗 **Parking** : Free for students | 07:30-20:00\n\n💬 Want to join a club? Contact Student Life: **vie.etudiante@fpst.tn** 😊",

        "identite_bot": "🤖 I'm **FPST SmartDesk**, the official AI assistant for the Private Faculty of Sciences and Technologies (FPST)!\n\n🎯 **Here's what I can do for you:**\n📄 Generate administrative documents (certificates, transcripts)\n📅 Check and manage timetables\n🎓 Provide info on programs, courses, and curricula\n📝 Guide you through registration and re-enrollment\n💰 Inform you about scholarships and financial aid\n💼 Help with internships and final year projects\n📜 Explain internal rules and regulations\n🔧 Assist with technical issues\n\n💬 Ask me anything about the faculty — I'm available 24/7! 😊",

        "inconnu": "🤔 I didn't quite understand your request, but no worries!\n\nTry one of these:\n• \"Certificate\" for a document\n• \"Timetable\" for your schedule\n• \"Programs\" for available majors\n• \"Registration\" for enrollment steps\n\nOr just ask your question and I'll do my best! 💪",

        "continuation": "💬 Could you tell me a bit more about what you need? I want to make sure I give you the right information! 😊"
    }
}

# ============================================
# NORMALISER LE TEXTE (supprimer accents)
# ============================================
def normalize_text(text: str) -> str:
    """Supprime les accents pour comparaison insensible aux accents."""
    nfkd = unicodedata.normalize('NFKD', text)
    return ''.join(c for c in nfkd if not unicodedata.combining(c)).lower()

# ============================================
# NETTOYER LE TEXTE ARABE
# ============================================
def clean_arabic(text: str) -> str:
    text = re.sub(r'[\u064B-\u065F]', '', text)
    text = re.sub(r'[^\w\s\u0600-\u06FF]', ' ', text)
    return text.strip()

# ============================================
# DÉTECTER LA LANGUE
# ============================================
def detect_language(message: str) -> str:
    try:
        arabic_chars = len(re.findall(r'[\u0600-\u06FF]', message))
        total_chars = len(message.replace(' ', ''))
        if total_chars > 0 and arabic_chars / total_chars > 0.3:
            return 'ar'
        lang = detect(message)
        if lang in ['fr', 'ar']:
            return lang
        if lang == 'en':
            return 'en'
        return 'fr'
    except:
        return 'fr'

# ============================================
# DÉTECTER L'INTENTION AVEC PRIORITÉ
# ============================================
def detect_intention(message: str) -> str:
    message_lower = message.lower()
    message_normalized = normalize_text(message)  # accent-insensitive version

    priority_order = [
        "demande_attestation_travail",
        "demande_attestation_scolarite",
        "demande_attestation_residence",
        "demande_attestation",
        "demande_releve_notes",
        "demande_emploi_temps",
        "demande_salle",
        "demande_notes",
        "demande_bourse",
        "demande_stage",
        "demande_calendrier",
        "question_reglement",
        "info_filiere",
        "demande_inscription",
        "demande_salaire",
        "demande_reclamation",
        "demande_formation",
        "demande_remboursement",
        "probleme_technique",
        "demande_rendez_vous",
        "demande_conge",
        "vie_campus",
        "identite_bot",
        "continuation",
        "demande_info",
        "remerciement",
        "au_revoir",
        "salutation"
    ]

    for intention in priority_order:
        keywords = INTENTIONS.get(intention, [])
        for keyword in keywords:
            if _keyword_matches(keyword, message_lower) or _keyword_matches(normalize_text(keyword), message_normalized):
                return intention

    return "inconnu"

# ============================================
# CALCULER LA CONFIANCE
# ============================================
def calculate_confidence(message: str, intention: str) -> float:
    if intention == "inconnu":
        return 0.0
    keywords = INTENTIONS.get(intention, [])
    matches = sum(1 for k in keywords if k in message)
    return min(matches / max(len(keywords), 1) * 100, 100.0)

# ============================================
# FONCTION PRINCIPALE
# ============================================
def analyze_message(message: str, language: str = None,
                    conversation_history: list = None) -> dict:
    """
    Hybrid intent classification pipeline:
      1. Trivial keyword check  (O(1), ultra-fast for greetings/bye/thanks)
      2. LLM classification     (Groq llama-3.1-8b-instant, context-aware)
      3. Full keyword fallback  (always available even if Groq is down)
    """
    if language and language in ['fr', 'en', 'ar']:
        detected_language = language
    else:
        detected_language = detect_language(message)

    message_clean = clean_arabic(message) if detected_language == 'ar' else message.lower()

    # ── Tier 1: trivial keyword check (no LLM cost for common greetings) ──
    intention = _trivial_keyword_intent(message_clean)
    classifier_used = "keyword_trivial"

    # ── Tier 2: LLM classification (context-aware, handles follow-ups) ──
    if intention is None:
        intention = classify_intent_llm(message, detected_language, conversation_history)
        if intention:
            classifier_used = "llm"

    # ── Tier 3: full keyword fallback ──
    if intention is None:
        intention = detect_intention(message_clean)
        classifier_used = "keyword_full"

    entities = []
    if detected_language == 'fr' and nlp_fr is not None:
        doc = nlp_fr(message)
        entities = [{"text": ent.text, "label": ent.label_} for ent in doc.ents]

    responses = REPONSES.get(detected_language, REPONSES['fr'])
    response = responses.get(intention, responses.get('inconnu', ''))

    return {
        "message_original": message,
        "language": detected_language,
        "intention": intention,
        "entities": entities,
        "response": response,
        "confidence": calculate_confidence(message_clean, intention),
        "classifier": classifier_used,
    }