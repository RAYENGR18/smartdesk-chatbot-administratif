# ============================================
# SERVICE RAG - Base de connaissances ENRICHIE
# Modèle : Groq (llama-3.3-70b-versatile)
# Prompts ultra-optimisés pour une faculté privée
# ============================================

import os
import sys
import time
from datetime import datetime
from dotenv import load_dotenv

def _safe_print(msg):
    """Print qui ne plante pas sur Windows avec encodage cp1252."""
    try:
        print(msg)
    except UnicodeEncodeError:
        try:
            print(msg.encode('ascii', errors='replace').decode('ascii'))
        except Exception:
            pass
from langchain_community.document_loaders import (
    PyPDFLoader,
    Docx2txtLoader,
    TextLoader
)
from langchain_text_splitters import RecursiveCharacterTextSplitter
# Paquets dédiés (recommandés) avec repli sur langchain_community
try:
    from langchain_huggingface import HuggingFaceEmbeddings
except ImportError:  # pragma: no cover
    from langchain_community.embeddings import HuggingFaceEmbeddings
try:
    from langchain_chroma import Chroma
except ImportError:  # pragma: no cover
    from langchain_community.vectorstores import Chroma
from groq import Groq
from langchain_core.prompts import PromptTemplate

# Mock Langchain interface to use direct Groq SDK without proxy issues
class GroqLLM:
    def __init__(self, api_key, model, temperature=0.15, max_tokens=700):
        # Timeout court pour éviter que l'API bloque longtemps en cas de réseau instable
        self.client = Groq(api_key=api_key, timeout=25)
        self.model = model
        self.temperature = temperature
        self.max_tokens = max_tokens

    def invoke(self, input_data):
        messages = []
        if isinstance(input_data, str):
            messages = [{"role": "user", "content": input_data}]
        elif isinstance(input_data, list):
            for msg in input_data:
                role = "user"
                if hasattr(msg, "type"):
                    if msg.type == "system":
                        role = "system"
                    elif msg.type == "ai":
                        role = "assistant"
                    else:
                        role = "user"
                content = msg.content if hasattr(msg, "content") else str(msg)
                messages.append({"role": role, "content": content})

        completion = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            max_tokens=self.max_tokens,
        )
        
        # Simple mock object to mimic Langchain response
        class Response:
            def __init__(self, content):
                self.content = content
            def __str__(self):
                return self.content
        
        return Response(completion.choices[0].message.content)

# .env : racine du projet puis dossier nlp/ (le premier trouvé gagne pour chaque variable)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(BASE_DIR, "..", ".env"))
load_dotenv(os.path.join(BASE_DIR, ".env"))

# ============================================
# CONFIGURATION (chemins absolus : le service peut être lancé depuis n'importe où)
# ============================================
DOCUMENTS_DIR         = os.path.join(BASE_DIR, "knowledge_base")
DOCUMENTS_DIR_STUDENT = os.path.join(DOCUMENTS_DIR, "student")
DOCUMENTS_DIR_PROF    = os.path.join(DOCUMENTS_DIR, "professor")
CHROMA_DIR            = os.getenv("CHROMA_DB_PATH") or os.path.join(BASE_DIR, "chroma_db")
CHROMA_DIR_PROF       = os.getenv("CHROMA_DB_PROF_PATH") or os.path.join(BASE_DIR, "chroma_db_prof")
EMBEDDING_MODEL       = os.getenv("EMBEDDING_MODEL", "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2")
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
GROQ_MODEL = "llama-3.3-70b-versatile"

# ============================================
# CONTEXTE PAR RÔLE UTILISATEUR
# ============================================
ROLE_CONTEXT = {
    "ETUDIANT": {
        "fr": (
            "👤 PROFIL : ÉTUDIANT\n"
            "Priorités : scolarité, notes, emploi du temps, stages, bourses, "
            "attestations de scolarité et de présence, règlement étudiant, vie campus.\n"
            "Adapte le vocabulaire et le ton pour un étudiant."
        ),
        "en": (
            "👤 PROFILE: STUDENT\n"
            "Priorities: enrollment, grades, timetable, internships, scholarships, "
            "certificates, campus life. Adapt tone for a student."
        ),
        "ar": (
            "👤 الملف الشخصي: طالب\n"
            "الأولويات: ترسيم، أعداد، جدول الحصص، تربص، منح، شهادة ترسيم، حياة الحرم الجامعي.\n"
            "تكلم مع الطالب بأسلوب مناسب."
        ),
    },
    "PROFESSEUR": {
        "fr": (
            "👤 PROFIL : PROFESSEUR\n"
            "Priorités : emploi du temps d'enseignement, matières attribuées, saisie des notes, "
            "attestations de travail / de salaire, demandes de congé, procédures RH.\n"
            "Adapte le vocabulaire pour un professionnel de l'enseignement."
        ),
        "en": (
            "👤 PROFILE: PROFESSOR\n"
            "Priorities: teaching schedule, assigned courses, grade entry, "
            "work certificates, leave requests, HR procedures."
        ),
        "ar": (
            "👤 الملف الشخصي: أستاذ\n"
            "الأولويات: جدول التدريس، المواد المسندة، إدخال الأعداد، شهادة عمل، طلبات إجازة.\n"
            "تكلم مع الأستاذ بأسلوب مهني."
        ),
    },
    "ADMIN": {
        "fr": (
            "👤 PROFIL : ADMINISTRATEUR\n"
            "Priorités : gestion des filières / salles / planning, documents officiels, "
            "gestion du personnel et des étudiants, procédures institutionnelles.\n"
            "Fournis des réponses complètes et officielles."
        ),
        "en": (
            "👤 PROFILE: ADMINISTRATOR\n"
            "Priorities: programs/rooms/timetable management, official documents, "
            "staff and student management, institutional procedures."
        ),
        "ar": (
            "👤 الملف الشخصي: مدير\n"
            "الأولويات: إدارة التخصصات والقاعات والبرمجة، وثائق رسمية، إدارة الموظفين والطلبة.\n"
            "قدم إجابات كاملة ورسمية."
        ),
    },
}

def get_role_context(user_role: str, language: str) -> str:
    """Returns the role-specific context string for RAG/Groq prompts."""
    role_upper = (user_role or "ETUDIANT").upper()
    # Normalize common variants
    if role_upper in ("USER", "STUDENT"):
        role_upper = "ETUDIANT"
    lang = language if language in ("fr", "en", "ar") else "fr"
    role_data = ROLE_CONTEXT.get(role_upper, ROLE_CONTEXT["ETUDIANT"])
    return role_data.get(lang, role_data["fr"])

# ============================================
# SYSTEM PROMPTS EXTRAORDINAIRES
# ============================================

SYSTEM_PROMPT_FR = """Tu es FPST SmartDesk (Faculty Assistant & Smart Technology Oracle), l'assistant IA officiel de la Faculté Privée des Sciences et Technologies (FPST).

🎓 TON IDENTITÉ :
- Tu es un assistant intelligent, chaleureux, professionnel et extrêmement compétent.
- Tu parles parfaitement français et arabe (standard et tunisien/derja).
- Tu as été créé par l'équipe PFE 2024-2025 et tu es propulsé par Groq AI.
- Tu es disponible 24h/24, 7j/7 pour aider étudiants, professeurs et personnel.

🎯 TES COMPÉTENCES :
1. Documents administratifs : attestations de scolarité, de travail, de résidence, relevés de notes
2. Emploi du temps & planning : consulter les créneaux, disponibilité des salles
3. Filières & programmes : informations sur les licences, masters, matières, coefficients
4. Inscriptions : procédures, dossiers, frais, dates limites
5. Examens : calendrier, notes, rattrapage, validation
6. Vie étudiante : clubs, bibliothèque, cafétéria, WiFi, parking
7. Stages & carrière : recherche, conventions, soutenances
8. Bourses : critères, montants, dossier à fournir
9. Règlement : absences, discipline, sanctions, plagiat

📋 RÈGLES DE RÉPONSE :
- COMMENCE TOUJOURS par répondre directement à la question. Ne te présente JAMAIS en début de réponse (pas de "Bonjour !", "Je suis FPST SmartDesk", etc.) — l'utilisateur te connaît déjà.
- Sois CONCIS et PRÉCIS. Va droit au but.
- Utilise des emojis pour rendre la conversation vivante (📅📄🎓📊💡).
- Structure tes réponses avec des puces et des numéros quand c'est utile.
- Si tu donnes un contact, inclus l'email ET le bureau.
- Si l'information n'est pas dans tes données, dis-le HONNÊTEMENT et oriente l'étudiant vers le bon service.
- Ne JAMAIS inventer des informations. Mieux vaut dire "je ne sais pas" que donner une fausse info.
- Termine toujours par une question ou une suggestion utile : "Puis-je vous aider avec autre chose ?"
- Si la question concerne un document, propose de le générer via le chatbot.
- Pour l'emploi du temps, rappelle que la page Planning est disponible dans l'application.

🏫 CONTEXTE FPST :
- Adresse : Zone Industrielle, Rue des Sciences, Tunis
- Horaires : Lun-Ven 08h00-18h00 | Sam 08h00-13h00
- Secrétariat : secretariat@fpst.tn | Bât. A, RDC
- Inscriptions : inscription@fpst.tn | Bureau 101
- Support technique : support@fpst.tn | Bureau 304

🔗 SUIVI DE CONVERSATION :
- Quand la question contient "ça", "cela", "pour ça", "il/elle", "les documents pour ça",
  ou toute référence ambiguë → utilise l'historique pour résoudre le référent et réponds sur LE BON SUJET.
- Exemple: [historique: stage] + "quels documents pour ça ?" → réponds sur les documents de stage, PAS sur les attestations.
- "oui" / "ok" / "plus" / "suite" → développe le sujet précédent avec des détails supplémentaires.

💡 STYLE :
- Professionnel mais accessible et chaleureux
- Empathique face aux problèmes des étudiants
- Encourageant et motivant
- Clair et structuré dans tes explications"""

SYSTEM_PROMPT_AR = """أنت فاستو (FPST SmartDesk)، المساعد الذكي الرسمي لكلية العلوم والتكنولوجيا الخاصة (FPST).

🎓 هويتك:
- أنت مساعد ذكي، ودود، محترف وكفء للغاية.
- تتحدث العربية الفصحى والتونسية (الدارجة) والفرنسية بطلاقة.
- تم إنشاؤك من قبل فريق مشروع تخرج 2024-2025.

🎯 تخصصاتك:
1. الوثائق الإدارية: شهادات، كشوف نقاط، وثائق التسجيل
2. جدول الحصص والبرنامج: مواعيد الدراسة، توفر القاعات
3. التخصصات والبرامج: معلومات عن الليسانس والماجستير
4. التسجيل: الإجراءات، الملفات المطلوبة، الرسوم
5. الامتحانات: الرزنامة، النتائج، الاستدراك
6. الحياة الجامعية: النوادي، المكتبة، المطعم
7. التربصات: البحث، الاتفاقيات، المناقشات
8. المنح: المعايير، المبالغ، الملف المطلوب

📋 قواعد الإجابة:
- ابدأ دائماً بالإجابة المباشرة على السؤال. لا تُعرّف بنفسك أبداً في بداية الإجابة (لا "مرحبا"، لا "أنا فاستو") — المستخدم يعرفك مسبقاً.
- كن مختصراً ودقيقاً. اذهب مباشرة للمعلومة.
- استخدم الرموز التعبيرية 📅📄🎓📊💡
- إذا سألك أحدهم بالدارجة التونسية، أجب بالدارجة أيضاً.
- لا تخترع معلومات أبداً. قل "ما عنديش المعلومة" أفضل من إجابة خاطئة.
- اختم دائماً بسؤال: "فمّا شيء آخر تحب تسأل عليه؟"

🔗 متابعة المحادثة:
- عندما يحتوي السؤال على "ذلك" أو "هذا" أو "فيه" أو "وثائقه" أو أي مرجع غامض
  → استخدم سياق المحادثة لتحديد الموضوع المقصود، وأجب عن الموضوع الصحيح.
- مثال: [السياق: تربص] + "ما الوثائق المطلوبة لذلك؟" → أجب عن وثائق التربص، وليس عن شهادة التسجيل.
- "نعم" / "أيوا" / "أكثر" / "واصل" → وسّع الإجابة عن الموضوع السابق بتفاصيل إضافية.

💡 الأسلوب:
- محترف لكن ودود ومقرّب
- متعاطف مع مشاكل الطلبة
- مشجع ومحفز
- واضح ومنظم في الشرح"""

# ============================================
# SYSTEM PROMPT ENGLISH
# ============================================
SYSTEM_PROMPT_EN = """You are FPST SmartDesk (Faculty Assistant & Smart Technology Oracle), the official AI assistant of the Private Faculty of Sciences and Technologies (FPST).

🎓 YOUR IDENTITY:
- You are an intelligent, warm, professional, and highly competent assistant.
- You speak English fluently.
- You were created by the PFE 2024-2025 team and powered by Groq AI.
- You are available 24/7 to help students, professors, and staff.

🎯 YOUR EXPERTISE:
1. Administrative documents: enrollment certificates, work certificates, residence certificates, transcripts
2. Timetable & scheduling: class slots, room availability
3. Programs & courses: information about bachelor's, master's, subjects, credits
4. Registration: procedures, required documents, fees, deadlines
5. Exams: calendar, grades, retakes, validation
6. Campus life: clubs, library, cafeteria, WiFi, parking
7. Internships & careers: search, agreements, defenses
8. Scholarships: criteria, amounts, required documents
9. Regulations: absences, discipline, sanctions, plagiarism

📋 RESPONSE RULES:
- ALWAYS start by directly answering the question. NEVER introduce yourself at the start of a response (no "Hello!", "I'm FPST SmartDesk", etc.) — the user already knows you.
- Be CONCISE and PRECISE. Get to the point.
- Use emojis to make the conversation lively (📅📄🎓📊💡).
- Structure your responses with bullets and numbers when useful.
- If you provide a contact, include email AND office.
- If the information is not in your data, say so HONESTLY and direct the student to the right service.
- NEVER invent information. Better to say "I don't know" than give wrong info.
- Always end with a question or useful suggestion: "Can I help you with anything else?"
- If the question is about a document, offer to generate it via the chatbot.
- For timetable questions, remind that the Planning page is available in the application.

🏫 FPST CONTEXT:
- Address: Industrial Zone, Science Street, Tunis
- Hours: Mon-Fri 08:00-18:00 | Sat 08:00-13:00
- Secretary: secretariat@fpst.tn | Bldg. A, Ground Floor
- Registration: inscription@fpst.tn | Office 101
- Tech support: support@fpst.tn | Office 304

🔗 CONVERSATION FOLLOW-UP:
- When the question contains "that", "it", "for that", "those documents", or any ambiguous reference
  → use the conversation history to resolve what is being referred to, and answer about THE CORRECT TOPIC.
- Example: [history: internship] + "what documents do I need for that?" → answer about internship documents, NOT about enrollment certificates.
- "yes" / "ok" / "more" / "sure" → expand on the previous topic with additional details.

💡 STYLE:
- Professional but approachable and warm
- Empathetic toward student problems
- Encouraging and motivating
- Clear and structured explanations"""

# ============================================
# QUERY REWRITING — résolution des références ambiguës
# ============================================
REWRITE_PROMPT = {
    'fr': """Tu es un expert en reformulation de requêtes conversationnelles pour un chatbot administratif universitaire.

MISSION CRITIQUE : Transformer la question de l'utilisateur en une requête 100% autonome et précise, en exploitant TOUT l'historique pour résoudre chaque référence ambiguë.

═══ RÉFÉRENCES À RÉSOUDRE (exemples concrets) ═══
• "ça" / "cela" / "ce truc" / "ce sujet"  →  le sujet du dernier échange
• "pour ça" / "à ce propos"               →  en rapport avec le dernier sujet
• "les documents pour ça"                 →  les documents requis pour [sujet précédent]
• "les conditions pour ça"                →  les conditions de [sujet précédent]
• "combien ça coûte"                      →  combien coûte [sujet précédent]
• "comment faire"                         →  comment faire [action discutée]
• "c'est quand"                           →  quelle est la date de [événement discuté]
• "il" / "elle" / "ils"                  →  le sujet / objet mentionné précédemment
• "le même" / "pareil"                   →  idem que le sujet précédent
• "aussi" / "et" (début de phrase)        →  en plus du sujet précédent
• "oui" / "exactement" / "vas-y"         →  confirmation → demande de détails supplémentaires

═══ EXEMPLES CONCRETS ═══
Historique: User: "Je veux faire un stage" | Bot: "Le stage M2 dure 4-6 mois…"
Question: "Quels documents pour ça ?"
→ Reformulation: "Quels documents sont nécessaires pour postuler à un stage à la FPST ?"

Historique: User: "Je veux une bourse" | Bot: "La bourse d'excellence couvre 25-100%…"
Question: "Comment je fais pour avoir ça ?"
→ Reformulation: "Quelle est la procédure pour obtenir une bourse à la FPST ?"

Historique: User: "Renseignements sur le master IA" | Bot: "Le Master IA dure 2 ans…"
Question: "C'est combien par an ?"
→ Reformulation: "Quel est le coût annuel de la formation Master IA à la FPST ?"

═══ RÈGLES ABSOLUES ═══
1. Retourne UNIQUEMENT la question reformulée — une seule ligne, sans guillemets, sans préfixe
2. Si la question est déjà complète et autonome → retourne-la TELLE QUELLE (sans modification)
3. Ne réponds JAMAIS à la question — reformule seulement
4. Inclus toujours le contexte de l'université (FPST) si le sujet le concerne
5. La reformulation doit être dans la même langue que la question originale

Historique complet (du plus ancien au plus récent) :
{history}

Question originale : {question}
Question reformulée :""",

    'en': """You are a conversational query rewriting expert for a university administrative chatbot.

CRITICAL MISSION: Transform the user's question into a 100% standalone and precise query, using the FULL conversation history to resolve every ambiguous reference.

═══ REFERENCES TO RESOLVE (concrete examples) ═══
• "that" / "it" / "this" / "those"       →  the subject of the last exchange
• "documents for that"                   →  documents required for [previous topic]
• "conditions for that"                  →  conditions of [previous topic]
• "how much does it cost"               →  how much does [previous topic] cost
• "how do I do that"                    →  how do I do [discussed action]
• "when is it"                          →  when is [discussed event]
• "the same" / "similar"               →  same as the previous topic
• "yes" / "sure" / "go ahead"          →  confirmation → request for more details on previous topic
• "and" / "also" (at sentence start)   →  in addition to previous topic

═══ CONCRETE EXAMPLES ═══
History: User: "I want to do an internship" | Bot: "The M2 internship lasts 4-6 months…"
Question: "What documents do I need for that?"
→ Rewrite: "What documents are required to apply for an internship at FPST?"

History: User: "Tell me about the AI master's program" | Bot: "The AI Master's lasts 2 years…"
Question: "How much does it cost per year?"
→ Rewrite: "What is the annual tuition fee for the AI Master's program at FPST?"

History: User: "I want a scholarship" | Bot: "The excellence scholarship covers 25-100%…"
Question: "How do I apply for that?"
→ Rewrite: "What is the application procedure for a scholarship at FPST?"

═══ ABSOLUTE RULES ═══
1. Return ONLY the rewritten question — one line, no quotes, no prefix
2. If the question is already complete and standalone → return it AS IS (no modification)
3. NEVER answer the question — only rewrite it
4. Always include university context (FPST) when relevant
5. The rewrite must be in the same language as the original question

Full conversation history (oldest to newest):
{history}

Original question: {question}
Rewritten question:""",

    'ar': """أنت خبير في إعادة صياغة الاستعلامات الحوارية لنظام chatbot إداري جامعي.

المهمة الحرجة: تحويل سؤال المستخدم إلى استعلام مستقل ودقيق 100%، باستخدام السياق الكامل للمحادثة لحل كل مرجع غامض.

═══ المراجع التي يجب حلها (أمثلة ملموسة) ═══
• "ذلك" / "هذا" / "هذه الحاجة"          →  موضوع آخر تبادل
• "الوثائق لـ ذلك"                     →  الوثائق المطلوبة لـ [الموضوع السابق]
• "شروط ذلك"                           →  شروط [الموضوع السابق]
• "قداش يكلف"                          →  قداش يكلف [الموضوع السابق]
• "كيفاش نعمل"                         →  كيفاش نعمل [الإجراء المناقَش]
• "وقتاش"                              →  وقتاش [الحدث المناقَش]
• "نعم" / "أيوا" / "واصل"              →  تأكيد → طلب تفاصيل إضافية عن الموضوع السابق
• "هو" / "هي" / "فيه"                  →  الموضوع المذكور سابقاً
• "نفس الشيء" / "برضو"                →  نفس الموضوع السابق

═══ أمثلة ملموسة ═══
السياق: المستخدم: "نحب نعمل تربص" | البوت: "التربص M2 يدوم 4-6 أشهر…"
السؤال: "اشنو الوثائق لذلك؟"
→ الإعادة: "اشنو الوثائق المطلوبة باش نتقدم لتربص في FPST؟"

السياق: المستخدم: "نحب منحة" | البوت: "منحة التميز تغطي 25-100%…"
السؤال: "كيفاش نعمل باش نجيبها؟"
→ الإعادة: "شنو الإجراءات باش نتقدم لمنحة في FPST؟"

═══ القواعد المطلقة ═══
1. أعد فقط السؤال المُعاد صياغته — سطر واحد، بدون علامات اقتباس، بدون بادئة
2. إذا كان السؤال كاملاً ومستقلاً بالفعل → أعده كما هو (بدون تعديل)
3. لا تجب على السؤال أبداً — أعد صياغته فقط
4. أدرج دائماً سياق الجامعة (FPST) عند الاقتضاء
5. يجب أن تكون إعادة الصياغة بنفس لغة السؤال الأصلي

السياق الكامل للمحادثة (من الأقدم إلى الأحدث):
{history}

السؤال الأصلي: {question}
السؤال المُعاد صياغته:"""
}

# ============================================
# PROMPT RAG FRANÇAIS — avec contexte conversation
# ============================================
RAG_PROMPT_FR = """Tu es FPST SmartDesk, l'assistant IA de la Faculté Privée des Sciences et Technologies (FPST).
MISSION : Répondre de manière experte, concise et utile en utilisant les informations de la base de données ci-dessous.
⚠️ LANGUE : Réponds TOUJOURS en FRANÇAIS. Même si les documents sont en arabe ou en anglais.

{role_context}

📋 INSTRUCTIONS STRICTES :
1. Commence DIRECTEMENT par la réponse — ne te présente jamais en début de message.
2. Utilise UNIQUEMENT les informations fournies dans le contexte documentaire ci-dessous.
3. Utilise l'historique de conversation pour comprendre les références ambiguës ("ça", "cela", "ce sujet", "pour ça", etc.) et rester cohérent avec le fil de la discussion.
4. Si la réponse N'EST PAS dans le contexte : dis "❌ Je n'ai pas cette information. Contactez [service approprié]."
5. Ne jamais INVENTER ni EXTRAPOLER des informations.
6. Utilise des emojis et des puces pour structurer ta réponse.
7. Termine par : "💬 Avez-vous d'autres questions ?"

Date actuelle : {current_date}
{conversation_section}
═══ INFORMATIONS DOCUMENTAIRES ═══
{context}
═══ FIN ═══

Question : {question}
Réponse :"""

# ============================================
# PROMPT RAG ARABE — avec contexte conversation
# ============================================
RAG_PROMPT_AR = """أنت فاستو (FPST SmartDesk)، المساعد الذكي لكلية العلوم والتكنولوجيا الخاصة.
المهمة: أجب بشكل دقيق ومختصر ومفيد باستخدام المعلومات أدناه فقط.
⚠️ اللغة: أجب دائماً بالعربية فقط.

{role_context}

📋 تعليمات صارمة:
1. ابدأ مباشرة بالإجابة — لا تُعرّف بنفسك أبداً.
2. استخدم فقط المعلومات الموجودة في السياق الوثائقي أدناه.
3. استخدم سياق المحادثة لفهم المراجع الغامضة ("ذلك"، "هذا"، "فيه"، إلخ) والبقاء متسقاً مع خيط النقاش.
4. إذا لم تجد الإجابة: قل "❌ ما عنديش هالمعلومة. تواصل مع [الخدمة المناسبة]."
5. لا تخترع أي معلومة أبداً.
6. استخدم رموز تعبيرية ونقاط لتنظيم الإجابة.
7. إذا سألك أحد بالدارجة، أجب بالدارجة.
8. اختم بـ: "💬 فمّا شيء آخر تحب تسأل عليه؟"
{conversation_section}
═══ معلومات وثائقية ═══
{context}
═══ نهاية ═══

السؤال: {question}
الإجابة:"""

# ============================================
# PROMPT RAG ENGLISH — avec contexte conversation
# ============================================
RAG_PROMPT_EN = """You are FPST SmartDesk, the AI assistant for the Private Faculty of Sciences and Technologies (FPST).
MISSION: Respond expertly, concisely, and helpfully using the information from the documents below.
⚠️ LANGUAGE: Always respond in ENGLISH only.

{role_context}

📋 STRICT INSTRUCTIONS:
1. Start DIRECTLY with the answer — never introduce yourself at the beginning.
2. Use ONLY the information provided in the documentary context below.
3. Use the conversation history to understand ambiguous references ("that", "it", "this", "for that", etc.) and stay consistent with the ongoing discussion.
4. If the answer is NOT in the context: say "❌ I don't have this information. Please contact [appropriate service]."
5. Never INVENT or EXTRAPOLATE information.
6. Use emojis and bullet points to structure your response.
7. End with: "💬 Do you have any other questions?"

Current date: {current_date}
{conversation_section}
═══ DOCUMENTARY INFORMATION ═══
{context}
═══ END ═══

Question: {question}
Response:"""

# ============================================
# HELPER — formater l'historique pour les prompts
# ============================================
def _format_history_text(conversation_history: list, max_turns: int = 6) -> str:
    """Formats conversation history as readable text for prompt injection."""
    if not conversation_history:
        return ""
    lines = []
    for turn in conversation_history[-max_turns * 2:]:
        role = turn.get("role", "user")
        content = (turn.get("content") or "").strip()
        if not content:
            continue
        prefix = "Assistant" if role == "assistant" else "User"
        # Truncate long bot answers to keep the prompt concise
        if role == "assistant" and len(content) > 350:
            content = content[:350] + "…"
        lines.append(f"{prefix}: {content}")
    return "\n".join(lines)

# ============================================
# QUERY REWRITER — résolution des références
# ============================================
# Patterns that signal the question likely needs context to be understood
_AMBIGUOUS_TOKENS = {
    'fr': ['ça', 'cela', 'ce ', 'cette ', 'pour ça', 'pour cela', 'le même', 'la même',
           'y ', ' en ', 'là-', 'duquel', 'laquelle', 'lesquels', 'à ce', 'ce sujet',
           'ce truc', 'ce domaine', ' il ', ' elle ', ' ils ', ' elles '],
    'en': ['that', ' it ', 'this', 'those', 'them ', 'the same', 'which one', 'for that',
           'for it', 'about that', 'about it', 'regarding that', 'the one', 'these'],
    'ar': ['ذلك', 'هذا', 'هذه', 'فيه', 'عليه', 'منه', 'فيها', 'عليها', 'منها',
           'هاذاكا', 'نفس', 'كيما', 'هو ', 'هي '],
}

def _needs_rewrite(message: str, language: str, history: list) -> bool:
    """Returns True if the message likely requires conversation context to be understood."""
    if not history:
        return False
    msg = message.lower()
    # Short messages (≤ 8 words) almost always need context when there's history
    if len(message.split()) <= 8:
        return True
    patterns = _AMBIGUOUS_TOKENS.get(language, []) + _AMBIGUOUS_TOKENS['en']
    return any(p in msg for p in patterns)

def rewrite_question(message: str, conversation_history: list,
                     language: str = 'fr') -> str:
    """Rewrites an ambiguous follow-up question into a fully standalone query.

    Uses Groq with temperature=0 (deterministic) and a tight token budget so the
    rewriting step adds < 2 s of latency.  Falls back to the original message on
    any error so the pipeline is never blocked.
    """
    if not _needs_rewrite(message, language, conversation_history):
        return message

    if not GROQ_API_KEY:
        return message

    history_text = _format_history_text(conversation_history, max_turns=6)
    prompt_template = REWRITE_PROMPT.get(language, REWRITE_PROMPT['fr'])
    prompt = prompt_template.format(history=history_text, question=message)

    try:
        client = Groq(api_key=GROQ_API_KEY, timeout=10)
        completion = client.chat.completions.create(
            model=GROQ_MODEL,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.0,
            max_tokens=120,
        )
        rewritten = completion.choices[0].message.content.strip().strip('"\'')
        # Strip any label prefix Groq might accidentally add
        for prefix in ("question autonome:", "standalone question:", "السؤال المستقل:",
                        "question:", "answer:", "réponse:"):
            if rewritten.lower().startswith(prefix):
                rewritten = rewritten[len(prefix):].strip()
        if rewritten:
            _safe_print(f"[REWRITE] '{message[:60]}' → '{rewritten[:80]}'")
            return rewritten
    except Exception as e:
        _safe_print(f"[WARN] Rewrite failed ({e}) — using original question")
    return message

# ============================================
# INITIALISER LES EMBEDDINGS
# ============================================
_embeddings = None

def get_embeddings():
    """Charge le modèle d'embeddings une seule fois, au premier besoin."""
    global _embeddings
    if _embeddings is None:
        _embeddings = HuggingFaceEmbeddings(model_name=EMBEDDING_MODEL)
    return _embeddings

def _chroma_dir(audience: str) -> str:
    return CHROMA_DIR_PROF if audience == "professor" else CHROMA_DIR

# ============================================
# CHARGER LES DOCUMENTS (d'un ou plusieurs dossiers)
# ============================================
def _load_from_dir(directory):
    """Charge tous les documents d'un dossier (txt/pdf/docx)."""
    documents = []
    if not os.path.exists(directory):
        os.makedirs(directory, exist_ok=True)
        return documents
    for filename in os.listdir(directory):
        filepath = os.path.join(directory, filename)
        if not os.path.isfile(filepath):
            continue
        try:
            if filename.endswith('.pdf'):
                loader = PyPDFLoader(filepath)
                documents.extend(loader.load())
            elif filename.endswith('.docx'):
                loader = Docx2txtLoader(filepath)
                documents.extend(loader.load())
            elif filename.endswith('.txt'):
                loader = TextLoader(filepath, encoding='utf-8')
                documents.extend(loader.load())
            _safe_print(f"[OK] Charge : {filepath}")
        except Exception as e:
            _safe_print(f"[ERR] {filepath}: {e}")
    return documents

def load_documents(audience="student"):
    """
    audience='student'   → knowledge_base/ + knowledge_base/student/
    audience='professor' → knowledge_base/professor/
    audience='all'       → tous les dossiers
    """
    if audience == "professor":
        return _load_from_dir(DOCUMENTS_DIR_PROF)
    elif audience == "all":
        docs  = _load_from_dir(DOCUMENTS_DIR)
        docs += _load_from_dir(DOCUMENTS_DIR_STUDENT)
        docs += _load_from_dir(DOCUMENTS_DIR_PROF)
        return docs
    else:  # student (défaut)
        docs  = _load_from_dir(DOCUMENTS_DIR)
        docs += _load_from_dir(DOCUMENTS_DIR_STUDENT)
        return docs

# ============================================
# CRÉER LA BASE VECTORIELLE
# ============================================
def create_vector_store(audience="student"):
    chroma_dir = _chroma_dir(audience)
    documents = load_documents(audience)

    if not documents:
        _safe_print(f"[WARN] Aucun document trouve pour audience={audience}")
        return None

    text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=800,
        chunk_overlap=150,
        separators=["\n\n", "\n", " "]
    )
    chunks = text_splitter.split_documents(documents)
    _safe_print(f"[OK] {len(chunks)} morceaux ({audience}) a partir de {len(documents)} docs")

    vector_store = Chroma.from_documents(
        documents=chunks,
        embedding=get_embeddings(),
        persist_directory=chroma_dir
    )

    _safe_print(f"[OK] Base vectorielle '{audience}' creee avec succes")
    return vector_store

# ============================================
# CHARGER LA BASE VECTORIELLE EXISTANTE
# ============================================
def load_vector_store(audience="student"):
    chroma_dir = _chroma_dir(audience)
    if os.path.exists(chroma_dir) and os.listdir(chroma_dir):
        return Chroma(
            persist_directory=chroma_dir,
            embedding_function=get_embeddings()
        )
    return None

# ============================================
# RECONSTRUIRE LA BASE VECTORIELLE
# ============================================
def rebuild_vector_store(audience="student"):
    """
    Vide la collection existante puis la recrée.
    On ne supprime plus le dossier chroma_db avec shutil.rmtree : sous Windows
    le fichier SQLite est verrouillé tant que le service tourne (WinError 32).
    """
    existing = load_vector_store(audience)
    if existing is not None:
        try:
            existing.delete_collection()
        except Exception as e:
            _safe_print(f"[WARN] Collection {audience} non supprimee: {e}")
    return create_vector_store(audience)

# ============================================
# INITIALISER LE LLM GROQ
# ============================================
def get_groq_llm(temperature=0.15, max_tokens=700):
    if not GROQ_API_KEY:
        raise ValueError("GROQ_API_KEY non configurée dans .env")
    return GroqLLM(
        api_key=GROQ_API_KEY,
        model=GROQ_MODEL,
        temperature=temperature,
        max_tokens=max_tokens
    )

def call_groq_with_retry(llm, input_data, max_retries=2):
    """Call Groq with retry logic for transient failures"""
    last_error = None
    for attempt in range(max_retries + 1):
        try:
            return llm.invoke(input_data)
        except Exception as e:
            last_error = e
            error_str = str(e).lower()
            # Don't retry on auth errors or invalid API key
            if 'authentication' in error_str or 'invalid' in error_str or '401' in error_str:
                raise e
            if attempt < max_retries:
                wait_time = (attempt + 1) * 1.5
                _safe_print(f"[RETRY] Groq retry {attempt + 1}/{max_retries} after {wait_time}s...")
                time.sleep(wait_time)
    raise last_error


def _build_retrieval_fallback(source_documents, language='fr'):
    """Retourne les snippets bruts si on a des docs, None sinon.
    Returning None lets ChatService fall through to direct Groq chat."""
    if not source_documents:
        return None  # No docs → signal caller to try another path

    # We have context — present the snippets raw (LLM unavailable)
    snippets = []
    for doc in source_documents[:2]:
        content = " ".join(doc.page_content.split())
        snippets.append(content[:400] + ("..." if len(content) > 400 else ""))

    if language == 'ar':
        header = "📋 هاني وجدت هذه المعلومات :"
        footer = "💬 هل تريد الاستفسار عن شيء آخر ؟"
    elif language == 'en':
        header = "📋 Here is the relevant information I found:"
        footer = "💬 Is there anything else I can help you with?"
    else:
        header = "📋 Voici les informations pertinentes trouvées :"
        footer = "💬 Puis-je vous aider avec autre chose ?"

    bullets = "\n".join([f"• {s}" for s in snippets])
    return f"{header}\n{bullets}\n\n{footer}"

# ============================================
# RÉPONDRE À UNE QUESTION AVEC RAG + GROQ
# ============================================
def answer_with_rag(question: str, language: str = 'fr', user_role: str = 'ETUDIANT',
                    conversation_history: list = None) -> dict:
    try:
        # Choisir le vector store selon le rôle
        audience = "professor" if user_role == "PROFESSEUR" else "student"
        vector_store = load_vector_store(audience)

        if vector_store is None:
            vector_store = create_vector_store(audience)
            if vector_store is None:
                if language == 'ar':
                    kb_msg = "قاعدة المعرفة تجري فهرستها الآن. يرجى المحاولة مجددا بعد لحظات."
                elif language == 'en':
                    kb_msg = "The knowledge base is being prepared. Please try again in a few moments."
                else:
                    kb_msg = "La base de connaissances est en cours de preparation. Veuillez reessayer dans quelques instants."
                return {
                    "answer": kb_msg,
                    "sources": [],
                    "used_rag": False
                }

        # Step 1: Rewrite ambiguous follow-up questions into standalone queries
        standalone_question = rewrite_question(question, conversation_history, language)

        # Use MMR (Maximal Marginal Relevance) for more diverse, higher-quality results
        retriever = vector_store.as_retriever(
            search_type="mmr",
            search_kwargs={"k": 6, "fetch_k": 12}
        )

        current_date = datetime.now().strftime("%d/%m/%Y à %H:%M")
        role_context = get_role_context(user_role, language)

        if language == 'ar':
            template = RAG_PROMPT_AR
        elif language == 'en':
            template = RAG_PROMPT_EN
        else:
            template = RAG_PROMPT_FR

        # Build conversation context section for the prompt
        if conversation_history:
            history_text = _format_history_text(conversation_history, max_turns=4)
            if language == 'ar':
                conversation_section = f"\n═══ سياق المحادثة ═══\n{history_text}\n═══ نهاية السياق ═══\n"
            elif language == 'en':
                conversation_section = f"\n═══ CONVERSATION CONTEXT ═══\n{history_text}\n═══ END OF CONTEXT ═══\n"
            else:
                conversation_section = f"\n═══ CONTEXTE DE LA CONVERSATION ═══\n{history_text}\n═══ FIN DU CONTEXTE ═══\n"
        else:
            conversation_section = ""

        # Inject date, role context and conversation section before building the PromptTemplate
        template = template.replace("{current_date}", current_date)
        template = template.replace("{role_context}", role_context)
        template = template.replace("{conversation_section}", conversation_section)

        prompt = PromptTemplate(
            template=template,
            input_variables=["context", "question"]
        )

        llm = get_groq_llm(temperature=0.15, max_tokens=900)

        # Step 2: Retrieve using the STANDALONE (rewritten) question for better recall
        source_documents = retriever.invoke(standalone_question)
        context = "\n\n".join(doc.page_content for doc in source_documents)

        # Step 3: Answer using the ORIGINAL question (natural phrasing) + retrieved docs + history
        final_prompt = prompt.format(context=context, question=question)

        try:
            llm_response = call_groq_with_retry(llm, final_prompt)
            answer_text = llm_response.content if hasattr(llm_response, "content") else str(llm_response)
        except Exception as llm_error:
            _safe_print(f"[WARN] RAG LLM failed, trying snippet fallback: {llm_error}")
            answer_text = _build_retrieval_fallback(source_documents, language)
            if answer_text is None:
                # No docs + no LLM → signal ChatService to try direct Groq
                return {"answer": None, "sources": [], "used_rag": False}

        sources = []
        for doc in source_documents:
            source = doc.metadata.get("source", "Document inconnu")
            # Nom du fichier seulement : on n'expose jamais le chemin du serveur
            source_clean = os.path.basename(str(source).replace("\\", "/"))
            source_clean = os.path.splitext(source_clean)[0].replace("_", " ").title()
            if source_clean not in sources:
                sources.append(source_clean)

        answer = answer_text.strip() if answer_text else None

        return {
            "answer": answer,
            "sources": sources,
            "used_rag": answer is not None
        }

    except Exception as e:
        _safe_print(f"[ERR] Erreur RAG: {e}")
        return {"answer": None, "sources": [], "used_rag": False}

# ============================================
# CHAT DIRECT AVEC GROQ (SANS RAG)
# ============================================
def chat_with_groq(message: str, language: str = 'fr', user_role: str = 'ETUDIANT',
                   student_context: dict = None,
                   conversation_history: list = None) -> dict:
    try:
        llm = get_groq_llm(temperature=0.25, max_tokens=1200)

        system_prompt = SYSTEM_PROMPT_AR if language == 'ar' else SYSTEM_PROMPT_EN if language == 'en' else SYSTEM_PROMPT_FR

        # Inject role context into the system prompt
        role_ctx = get_role_context(user_role, language)
        system_prompt += f"\n\n──────────────────────\n{role_ctx}\n──────────────────────"

        # Inject student profile into system prompt ONLY (never into the user message)
        if student_context:
            profile_parts = []
            if student_context.get('firstName'):
                profile_parts.append(f"Prénom: {student_context['firstName']}")
            if student_context.get('lastName'):
                profile_parts.append(f"Nom: {student_context['lastName']}")
            if student_context.get('fieldOfStudy'):
                profile_parts.append(f"Filière: {student_context['fieldOfStudy']}")
            if student_context.get('academicLevel'):
                profile_parts.append(f"Niveau: {student_context['academicLevel']}")
            if student_context.get('studentId'):
                profile_parts.append(f"N° étudiant: {student_context['studentId']}")
            if profile_parts:
                if language == 'ar':
                    system_prompt += f"\n\n📌 معلومات المستخدم المُسجَّل: {', '.join(profile_parts)}"
                elif language == 'en':
                    system_prompt += f"\n\n📌 Logged-in user profile: {', '.join(profile_parts)}"
                else:
                    system_prompt += f"\n\n📌 Profil de l'utilisateur connecté: {', '.join(profile_parts)}"
                system_prompt += "\n(Utilise ces infos pour personnaliser ta réponse si pertinent, mais réponds d'abord à la question posée.)"

        # Date in the right language
        now_str = datetime.now().strftime('%d/%m/%Y %H:%M')
        if language == 'ar':
            current_info = f"\n\n📅 التاريخ والوقت الحاليان: {now_str}"
            lang_enforce = (
                "\n\n⚠️ قاعدة صارمة: يجب أن تُجيب دائماً باللغة العربية فقط."
                "\n\n💬 إذا قال المستخدم 'نعم' أو 'أكمل' أو 'أكثر' أو 'بالتفصيل' أو ما شابه — "
                "راجع سياق المحادثة السابقة وقدّم معلومات إضافية عن الموضوع المطروح. "
                "لا تقل أبداً إنك لم تفهم."
            )
        elif language == 'en':
            current_info = f"\n\n📅 Current date and time: {now_str}"
            lang_enforce = (
                "\n\n⚠️ Strict rule: Always respond in ENGLISH only."
                "\n\n💬 Conversation rule: If the user says 'yes', 'ok', 'more', 'sure', 'please', "
                "'continue', 'go ahead', 'tell me more', 'details', or any short affirmative — "
                "look at the conversation history above and expand on the previous topic with MORE "
                "details and useful information. NEVER say you didn't understand. "
                "NEVER ask for clarification — instead, provide the most logical continuation."
            )
        else:
            current_info = f"\n\n📅 Date et heure actuelles : {now_str}"
            lang_enforce = (
                "\n\n⚠️ Règle stricte: Réponds TOUJOURS en français uniquement."
                "\n\n💬 Règle conversation: Si l'utilisateur dit 'oui', 'ok', 'plus', 'suite', "
                "'continuer', 'dis-moi plus', 'détails', ou toute réponse courte affirmative — "
                "regarde l'historique de la conversation ci-dessus et développe le sujet précédent "
                "avec PLUS de détails et d'informations utiles. Ne dis JAMAIS que tu n'as pas compris. "
                "Ne demande JAMAIS de clarification — fournis plutôt la suite logique."
            )
        system_prompt += current_info + lang_enforce

        from langchain_core.messages import SystemMessage, HumanMessage, AIMessage

        # Build multi-turn message list
        messages = [SystemMessage(content=system_prompt)]

        # Inject conversation history (up to last 8 exchanges = 16 messages)
        if conversation_history:
            for turn in conversation_history[-16:]:
                role = turn.get("role", "user")
                content = turn.get("content", "")
                if not content:
                    continue
                if role == "assistant":
                    messages.append(AIMessage(content=content))
                else:
                    messages.append(HumanMessage(content=content))

        # Current user message (always last)
        messages.append(HumanMessage(content=message))

        response = call_groq_with_retry(llm, messages)

        return {
            "answer": response.content,
            "used_groq": True
        }

    except Exception as e:
        _safe_print(f"[ERR] Erreur Groq chat: {e}")
        import traceback
        traceback.print_exc()
        return {
            "answer": None,
            "used_groq": False
        }

# ============================================
# INDEXER UN NOUVEAU DOCUMENT
# ============================================
def index_document(filepath: str) -> bool:
    try:
        if filepath.endswith('.pdf'):
            loader = PyPDFLoader(filepath)
        elif filepath.endswith('.docx'):
            loader = Docx2txtLoader(filepath)
        elif filepath.endswith('.txt'):
            loader = TextLoader(filepath, encoding='utf-8')
        else:
            return False

        documents = loader.load()

        text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=800,
            chunk_overlap=150,
            separators=["\n═", "\n────", "\n\n", "\n", " "]
        )
        chunks = text_splitter.split_documents(documents)

        vector_store = load_vector_store()
        if vector_store:
            vector_store.add_documents(chunks)
        else:
            Chroma.from_documents(
                documents=chunks,
                embedding=get_embeddings(),
                persist_directory=CHROMA_DIR
            )

        _safe_print(f"[OK] Document indexe : {filepath} ({len(chunks)} morceaux)")
        return True

    except Exception as e:
        _safe_print(f"[ERR] Erreur indexation: {e}")
        return False