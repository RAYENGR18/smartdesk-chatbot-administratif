package com.chatbot.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Service
public class NlpService {

    @Value("${nlp.url:http://localhost:8000}")
    private String nlpBaseUrl;

    private final RestTemplate restTemplate;

    public NlpService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
    }

    // ============================================
    // ANALYSER UN MESSAGE AVEC NLP
    // ============================================
    public Map<String, Object> analyzeMessage(String message) {
        return analyzeMessage(message, null);
    }

    public Map<String, Object> analyzeMessage(String message, String language) {
        return analyzeMessage(message, language, null);
    }

    public Map<String, Object> analyzeMessage(String message, String language,
                                               List<Map<String, String>> conversationHistory) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("message", message);
            body.put("language", language != null ? language : "fr");
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                body.put("conversation_history", conversationHistory);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlpBaseUrl + "/analyze", request, Map.class);

            return response.getBody();

        } catch (Exception e) {
            System.err.println("NLP service unreachable: " + e.getMessage());
            return buildLocalNlpResponse(message, language);
        }
    }

    // ============================================
    // RAG — backward-compatible overload
    // ============================================
    public Map<String, Object> askRag(String question, String language) {
        return askRag(question, language, "ETUDIANT");
    }

    // ============================================
    // RAG — with user role context
    // ============================================
    public Map<String, Object> askRag(String question, String language, String userRole) {
        return askRag(question, language, userRole, null);
    }

    // ============================================
    // RAG — with conversation history (context-aware)
    // ============================================
    public Map<String, Object> askRag(String question, String language, String userRole,
                                       List<Map<String, String>> conversationHistory) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("question", question);
            body.put("language", language != null ? language : "fr");
            body.put("user_role", userRole != null ? userRole : "ETUDIANT");
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                body.put("conversation_history", conversationHistory);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlpBaseUrl + "/rag/ask", request, Map.class);

            return response.getBody();

        } catch (Exception e) {
            System.err.println("RAG service unreachable: " + e.getMessage());
            Map<String, Object> defaultResponse = new HashMap<>();
            defaultResponse.put("answer", null);
            defaultResponse.put("used_rag", false);
            return defaultResponse;
        }
    }

    // ============================================
    // CHAT DIRECT AVEC GROQ
    // ============================================
    public Map<String, Object> chatWithGroq(String message, String language,
                                             Map<String, Object> studentContext) {
        return chatWithGroq(message, language, studentContext, "ETUDIANT", null);
    }

    public Map<String, Object> chatWithGroq(String message, String language,
                                             Map<String, Object> studentContext,
                                             String userRole) {
        return chatWithGroq(message, language, studentContext, userRole, null);
    }

    public Map<String, Object> chatWithGroq(String message, String language,
                                             Map<String, Object> studentContext,
                                             String userRole,
                                             List<Map<String, String>> conversationHistory) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("message", message);
            body.put("language", language != null ? language : "fr");
            body.put("user_role", userRole != null ? userRole : "ETUDIANT");
            if (studentContext != null) {
                body.put("student_context", studentContext);
            }
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                body.put("conversation_history", conversationHistory);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlpBaseUrl + "/groq/chat", request, Map.class);

            Map responseBody = response.getBody();
            if (responseBody != null) {
                String answer = (String) responseBody.get("answer");
                if (answer != null) {
                    String lower = answer.toLowerCase();
                    if (lower.contains("indisponible") || lower.contains("erreur")
                            || lower.contains("temporarily unavailable") || lower.contains("external ai")) {
                        responseBody.put("answer", null);
                        responseBody.put("used_groq", false);
                    }
                }
            }

            return responseBody;

        } catch (Exception e) {
            System.err.println("Groq service unreachable: " + e.getMessage());
            Map<String, Object> defaultResponse = new HashMap<>();
            defaultResponse.put("answer", null);
            defaultResponse.put("used_groq", false);
            return defaultResponse;
        }
    }

    // ============================================
    // REWRITE — reformulation de requête ambiguë
    // ============================================
    public String rewriteQuery(String message, String language,
                                List<Map<String, String>> conversationHistory) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("message", message);
            body.put("language", language != null ? language : "fr");
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                body.put("conversation_history", conversationHistory);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nlpBaseUrl + "/rewrite", request, Map.class);

            Map<?, ?> responseBody = response.getBody();
            if (responseBody != null) {
                String rewritten = (String) responseBody.get("rewritten");
                if (rewritten != null && !rewritten.isBlank()) {
                    return rewritten;
                }
            }
        } catch (Exception e) {
            System.err.println("Rewrite service unreachable: " + e.getMessage());
        }
        return message; // fallback: keep original
    }

    // ============================================
    // RÉPONSE NLP LOCALE (FALLBACK INTELLIGENT)
    // ============================================
    private Map<String, Object> buildLocalNlpResponse(String message, String language) {
        Map<String, Object> result = new HashMap<>();
        String msgLower = message.toLowerCase();
        String lang = language != null ? language : "fr";

        String intention = "inconnu";
        String response;

        if ("ar".equals(lang)) {
            if (containsAny(msgLower, "مرحبا", "سلام", "اهلا", "أهلا", "bonjour", "hello", "salut")) {
                intention = "salutation";
                response = "مرحبا! أنا FPST SmartDesk، مساعدك الإداري الذكي. كيف يمكنني مساعدتك اليوم?\n\n📄 وثائق إدارية\n📅 جدول الحصص\n🎓 معلومات أكاديمية\n📝 التسجيل والمنح";
            } else if (containsAny(msgLower, "شهادة", "وثيقة", "وثائق", "attestation", "certificat", "document")) {
                intention = "demande_attestation";
                response = "📄 أستطيع مساعدتك في الوثائق الإدارية. اختر نوع الوثيقة واستعمل زر الوثائق لإرسال الطلب.";
            } else if (containsAny(msgLower, "جدول", "توقيت", "حصة", "emploi du temps", "planning")) {
                intention = "demande_emploi_temps";
                response = "📅 افتح صفحة التخطيط في التطبيق لمشاهدة الحصص والقاعات بالتفصيل.";
            } else if (containsAny(msgLower, "عدد", "أعداد", "نتيجة", "نتائج", "note", "notes", "grades")) {
                intention = "demande_notes";
                response = "📊 يمكنك الاطلاع على أعدادك من تبويب النتائج، أو طلب كشف أعداد رسمي عبر زر الوثائق.";
            } else if (containsAny(msgLower, "منحة", "منح", "bourse", "scholarship")) {
                intention = "demande_bourse";
                response = "🏅 توجد منح امتياز ومنح اجتماعية حسب المعدل والوضعية الاجتماعية.";
            } else {
                response = "💡 أنا FPST SmartDesk، المساعد الذكي. يمكنني مساعدتك في الوثائق، الجداول، النتائج، التسجيل، المنح، والتربصات.";
            }

            result.put("message_original", message);
            result.put("language", lang);
            result.put("intention", intention);
            result.put("response", response);
            result.put("confidence", 75.0);
            return result;
        }

        if (containsAny(msgLower, "bonjour", "salut", "hello", "bonsoir", "coucou", "hey", "hi", "good morning")) {
            intention = "salutation";
            response = "en".equals(lang)
                    ? "Hello! I'm **FPST SmartDesk**, the AI assistant for FPST.\n\nHow can I help you today?\n📄 Documents | 📅 Schedule | 🎓 Programs | 📝 Registration"
                    : "Bonjour ! Je suis **FPST SmartDesk**, votre assistant IA FPST.\n\nComment puis-je vous aider ?\n📄 Documents | 📅 Planning | 🎓 Filières | 📝 Inscriptions";
        } else if (containsAny(msgLower, "attestation", "certificat", "document", "certificate")) {
            intention = "demande_attestation";
            response = "en".equals(lang)
                    ? "📄 I can help with documents! Click 📄 below to generate yours directly."
                    : "📄 Je peux vous aider avec les documents ! Cliquez sur 📄 ci-dessous pour générer le vôtre.";
        } else if (containsAny(msgLower, "emploi du temps", "planning", "horaire", "cours", "timetable", "schedule")) {
            intention = "demande_emploi_temps";
            response = "en".equals(lang)
                    ? "📅 Open the **Planning** page in this app to see your complete schedule."
                    : "📅 Consultez la page **Planning** de cette application pour votre emploi du temps complet.";
        } else if (containsAny(msgLower, "note", "résultat", "releve", "relevé", "moyenne", "examen", "grades", "results", "transcript")) {
            intention = "demande_notes";
            response = "en".equals(lang)
                    ? "📊 Check your **grades** in the Grades tab or request an official transcript via the document button."
                    : "📊 Consultez vos **notes** dans l'onglet Notes ou demandez un relevé officiel via le bouton document.";
        } else if (containsAny(msgLower, "filière", "filiere", "spécialité", "informatique", "master", "licence", "program", "major")) {
            intention = "info_filiere";
            response = "en".equals(lang)
                    ? "🎓 FPST offers **6 Bachelor's** and **4 Master's** programs!\n\nBachelor's: CS, Software Eng., Networks, Management, Digital Marketing, IS\nMaster's: AI & Data Science, Cybersecurity, MIS, Finance"
                    : "🎓 La FPST propose **6 Licences** et **4 Masters** !\n\nLicences : Informatique, Génie Logiciel, Réseaux, Sciences de Gestion, Marketing Digital, SI\nMasters : IA & Data Science, Cybersécurité, MSI, Finance";
        } else if (containsAny(msgLower, "inscription", "inscrire", "frais", "dossier", "register", "enroll", "tuition")) {
            intention = "demande_inscription";
            response = "en".equals(lang)
                    ? "📝 Registration: pre-register, prepare your file (ID, diploma, photos), pay fees (Bachelor: 4500 DT/yr | Master: 5500 DT/yr).\n📧 inscription@fpst.tn | Office 101"
                    : "📝 Inscription : pré-inscrivez-vous, préparez le dossier (CIN, bac, photos), payez les frais (Licence: 4500 DT/an | Master: 5500 DT/an).\n📧 inscription@fpst.tn | Bureau 101";
        } else if (containsAny(msgLower, "bourse", "aide financière", "réduction", "scholarship", "financial aid")) {
            intention = "demande_bourse";
            response = "en".equals(lang)
                    ? "🏅 Scholarships:\n• Top of class → 100% waiver\n• GPA ≥ 15/20 → 25% discount\n• Social scholarship → 20-50% discount\n📧 finance@fpst.tn | Office 103"
                    : "🏅 Bourses :\n• Major de promo → Exonération 100%\n• Moyenne ≥ 15/20 → Réduction 25%\n• Bourse sociale → Réduction 20-50%\n📧 finance@fpst.tn | Bureau 103";
        } else if (containsAny(msgLower, "club", "clubs", "association", "bibliothèque", "biblio",
                "cafétéria", "cafeteria", "sport", "sports", "hackathon", "codefpst",
                "campus life", "student life", "library", "wifi", "parking",
                "نادي", "نوادي", "مكتبة", "كافيتيريا", "رياضة")) {
            intention = "vie_campus";
            response = "en".equals(lang)
                    ? "🏫 **Campus Life at FPST**:\n\n🎓 Student Clubs:\n• 💻 CodeFPST — Coding Club\n• 🚀 Entrepreneurship Club\n• 🎭 Culture & Arts Club\n• ⚽ Sports Club\n• 🏆 Regular Hackathons\n\n📚 Library: 10,000+ refs | Mon-Fri 08:00-19:00\n🍽️ Cafeteria: Building B | 08:00-17:00\n🏋️ Sports Hall: Tue & Thu 17:00-20:00\n📶 WiFi: Full campus coverage\n🚗 Free parking 07:30-20:00\n\n💬 Contact: vie.etudiante@fpst.tn"
                    : "🏫 **Vie sur le campus FPST** :\n\n🎓 Clubs étudiants :\n• 💻 CodeFPST — Club informatique\n• 🚀 Club Entrepreneuriat\n• 🎭 Club Culture & Arts\n• ⚽ Club Sports\n• 🏆 Hackathons réguliers\n\n📚 Bibliothèque : 10 000+ refs | Lun-Ven 08h-19h\n🍽️ Cafétéria : Bât. B | 08h-17h\n🏋️ Salle de sport : Mar & Jeu 17h-20h\n📶 WiFi campus complet\n🚗 Parking gratuit 07h30-20h\n\n💬 Contact : vie.etudiante@fpst.tn";
        } else if (containsAny(msgLower, "what do you do", "what can you do", "who are you",
                "what are you", "how can you help", "introduce yourself", "what is fpst smartdesk",
                "tu fais quoi", "que peux-tu faire", "qui es-tu", "présente-toi",
                "شنو تعمل", "اشكون انت", "من انت")) {
            intention = "identite_bot";
            response = "en".equals(lang)
                    ? "🤖 I'm **FPST SmartDesk**, the official AI assistant for FPST Faculty!\n\n🎯 I can help you with:\n📄 Administrative documents\n📅 Timetable & schedule\n🎓 Programs & courses\n📝 Registration & enrollment\n💰 Scholarships\n💼 Internships & PFE\n📜 Regulations\n🔧 Technical support\n\n💬 Ask me anything — I'm here 24/7! 😊"
                    : "🤖 Je suis **FPST SmartDesk**, l'assistant IA officiel de la faculté FPST !\n\n🎯 Je peux vous aider avec :\n📄 Documents administratifs\n📅 Emploi du temps & planning\n🎓 Filières & programmes\n📝 Inscription & réinscription\n💰 Bourses\n💼 Stages & PFE\n📜 Règlement\n🔧 Support technique\n\n💬 Posez-moi n'importe quelle question — Je suis là 24h/24 ! 😊";
        } else if (containsAny(msgLower, "merci", "thank", "parfait", "super", "thanks")) {
            intention = "remerciement";
            response = "en".equals(lang)
                    ? "You're welcome! 😊 I'm available 24/7."
                    : "Avec plaisir ! 😊 Je suis là 24h/24 pour vous aider.";
        } else if (containsAny(msgLower, "au revoir", "bye", "à bientôt", "goodbye")) {
            intention = "au_revoir";
            response = "en".equals(lang)
                    ? "Goodbye! 👋 Good luck! 📧 secretariat@fpst.tn"
                    : "Au revoir ! 👋 Bonne continuation ! 📧 secretariat@fpst.tn";
        } else if (containsAny(msgLower, "stage", "pfe", "soutenance", "internship")) {
            intention = "demande_stage";
            response = "en".equals(lang)
                    ? "💼 Internships: L2 (1 month), L3 PFE (2 months), M2 (4-6 months)\n📍 Office 203 | stages@fpst.tn"
                    : "💼 Stages : L2 (1 mois), L3 PFE (2 mois), M2 (4-6 mois)\n📍 Bureau 203 | stages@fpst.tn";
        } else if (containsAny(msgLower, "calendrier", "date", "vacances", "rentrée", "calendar", "vacation")) {
            intention = "demande_calendrier";
            response = "en".equals(lang)
                    ? "📅 Academic Calendar:\n🔵 S1: Sep 15 – Jan 18\n🟢 S2: Feb 10 – Jun 14\n🎄 Winter break: Dec 22 – Jan 5"
                    : "📅 Calendrier :\n🔵 S1 : 15 Sept – 18 Jan\n🟢 S2 : 10 Fév – 14 Juin\n🎄 Vacances hiver : 22 Déc – 5 Jan";
        } else if (containsAny(msgLower, "réclamation", "plainte", "problème", "complaint", "issue")) {
            intention = "demande_reclamation";
            response = "en".equals(lang)
                    ? "📢 To file a complaint: describe the issue, response within 24h, processing in 5 days.\n📧 reclamation@fpst.tn | Office 110"
                    : "📢 Réclamation : décrivez le problème, accusé sous 24h, traitement sous 5 jours.\n📧 reclamation@fpst.tn | Bureau 110";
        } else {
            response = "en".equals(lang)
                    ? "💡 I'm **FPST SmartDesk**! I can help with:\n📄 Documents | 📅 Schedule | 🎓 Programs | 📝 Registration\n💰 Scholarships | 💼 Internships | 📜 Rules | 🔧 Support\n\nAsk your question! 😊"
                    : "💡 Je suis **FPST SmartDesk** ! Je peux vous aider avec :\n📄 Documents | 📅 Planning | 🎓 Filières | 📝 Inscription\n💰 Bourses | 💼 Stages | 📜 Règlement | 🔧 Support\n\nPosez votre question ! 😊";
        }

        result.put("message_original", message);
        result.put("language", lang);
        result.put("intention", intention);
        result.put("response", response);
        result.put("confidence", 75.0);

        return result;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
