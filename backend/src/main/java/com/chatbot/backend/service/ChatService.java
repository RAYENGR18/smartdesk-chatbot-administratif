package com.chatbot.backend.service;

import com.chatbot.backend.model.ChatMessage;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.ChatMessageRepository;
import com.chatbot.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NlpService nlpService;

    /*
     * ┌─────────────────────────────────────────────────────────────┐
     * │                  PIPELINE ARCHITECTURE                       │
     * │                                                              │
     * │  User message                                                │
     * │       │                                                      │
     * │       ▼                                                      │
     * │  [NLP CLASSIFY] — detect trivial/action intents only        │
     * │       │                                                      │
     * │       ├─ TRIVIAL (hello/bye/thanks/who-are-you/campus)      │
     * │       │    └→ Pre-built NLP response (instant, no AI)       │
     * │       │                                                      │
     * │       ├─ ACTION (attestation/timetable/room explicit request)│
     * │       │    └→ NLP-generated form/document response          │
     * │       │                                                      │
     * │       └─ EVERYTHING ELSE (knowledge / follow-ups / unknown) │
     * │            │                                                 │
     * │            ▼                                                 │
     * │       [QUERY REWRITER] — resolve "that/it/this/ça" → topic  │
     * │            │             using full conversation history     │
     * │            ▼                                                 │
     * │       [RAG RETRIEVER] — semantic search on knowledge base   │
     * │            │             using the rewritten standalone query│
     * │            ▼                                                 │
     * │       [GROQ GENERATOR] — answer from docs + history         │
     * │            │                                                 │
     * │            ▼ (if RAG has no relevant docs)                  │
     * │       [DIRECT GROQ] — general AI with full history          │
     * │            │                                                 │
     * │            ▼ (last resort)                                   │
     * │       [FALLBACK] — generic helpful message                  │
     * └─────────────────────────────────────────────────────────────┘
     *
     * KEY DESIGN DECISION: Classification output is NEVER used to route
     * knowledge questions. Only trivial/action intents are routed by NLP.
     * This completely eliminates misclassification-driven wrong answers.
     */

    // Trivial: instant keyword-based responses, no context needed, no AI
    private static final List<String> TRIVIAL_INTENTIONS = List.of(
            "salutation",
            "au_revoir",
            "remerciement",
            "identite_bot",
            "vie_campus"
    );

    // Action: NLP-generated forms or UI triggers — user must explicitly name
    // the document/schedule (e.g. "attestation de scolarité", "emploi du temps")
    // A contextual follow-up like "what documents for that?" will NOT match
    // these because their keyword patterns require the explicit term.
    private static final List<String> ACTION_INTENTIONS = List.of(
            "demande_attestation_travail",
            "demande_attestation_scolarite",
            "demande_attestation_residence",
            "demande_emploi_temps",
            "demande_salle"
    );

    public ChatMessage sendMessage(String userMessage, String username) {
        return sendMessage(userMessage, username, null, null);
    }

    public ChatMessage sendMessage(String userMessage, String username,
                                   String language, Map<String, Object> studentContext) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        String userRole = user.getRole() != null ? user.getRole().name() : "USER";

        // ── Step 1: Full conversation history (drives both rewriting and AI answers) ──
        List<Map<String, String>> conversationHistory = buildConversationHistory(user.getId());

        // ── Step 2: Classify on the ORIGINAL message to detect trivial/action intents ──
        // This is the ONLY place classification affects behavior.
        // For everything else (knowledge, follow-ups, ambiguous), classification is
        // intentionally ignored — routing is handled by the Rewriter → RAG → Groq pipeline.
        Map<String, Object> nlpResult = nlpService.analyzeMessage(userMessage, language, conversationHistory);
        String intention = (String) nlpResult.get("intention");
        String detectedLanguage = language != null ? language : (String) nlpResult.get("language");

        String botResponse;

        if (TRIVIAL_INTENTIONS.contains(intention) || ACTION_INTENTIONS.contains(intention)) {
            // ── Fast path: pre-built NLP response ──
            // No rewriting, no RAG, no Groq needed.
            // These responses are correct regardless of conversation context.
            botResponse = (String) nlpResult.get("response");

        } else {
            // ── Knowledge / follow-up / ambiguous path ──
            // Step 3: Rewrite the query BEFORE retrieval to resolve contextual references.
            // "What documents do I need for that?" + [internship history]
            //   → "What documents are required to apply for an internship at FPST?"
            String rewrittenMessage = nlpService.rewriteQuery(userMessage, detectedLanguage, conversationHistory);

            // Step 4: RAG → Groq pipeline using the rewritten standalone query.
            // Classification output does NOT affect routing here.
            botResponse = answerWithRagThenGroq(
                    rewrittenMessage, detectedLanguage, studentContext,
                    nlpResult, userRole, conversationHistory);
        }

        // ── Step 5: Safety net — never show error text to the user ──
        botResponse = sanitizeResponse(botResponse, nlpResult, detectedLanguage);

        // ── Step 6: Persist (original message, not rewritten, for natural history) ──
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserMessage(userMessage);
        chatMessage.setBotResponse(botResponse);
        chatMessage.setIntention(intention);
        chatMessage.setLanguage(detectedLanguage);
        chatMessage.setUser(user);

        return chatMessageRepository.save(chatMessage);
    }

    /**
     * Core knowledge answering pipeline: RAG → Groq → NLP fallback.
     * Always uses the rewritten (standalone) query for retrieval.
     */
    private String answerWithRagThenGroq(String rewrittenMessage, String language,
                                          Map<String, Object> studentContext,
                                          Map<String, Object> nlpResult,
                                          String userRole,
                                          List<Map<String, String>> conversationHistory) {
        // Try 1: RAG — document-grounded answer (best quality for factual questions)
        String ragAnswer = tryRag(rewrittenMessage, language, userRole, conversationHistory);
        if (isValidAnswer(ragAnswer)) return ragAnswer;

        // Try 2: Direct Groq — general AI with full conversation history
        String groqAnswer = tryGroq(rewrittenMessage, language, studentContext, userRole, conversationHistory);
        if (isValidAnswer(groqAnswer)) return groqAnswer;

        // Try 3: NLP pre-built response (always available, no network required)
        String nlpResponse = (String) nlpResult.get("response");
        if (isValidAnswer(nlpResponse)) return nlpResponse;

        return getGenericFallbackResponse(language);
    }

    private String tryGroq(String message, String language,
                            Map<String, Object> studentContext,
                            String userRole,
                            List<Map<String, String>> conversationHistory) {
        try {
            Map<String, Object> groqResult = nlpService.chatWithGroq(
                    message, language, studentContext, userRole, conversationHistory);
            return (String) groqResult.get("answer");
        } catch (Exception e) {
            System.err.println("Groq call failed: " + e.getMessage());
            return null;
        }
    }

    private String tryRag(String message, String language, String userRole,
                           List<Map<String, String>> conversationHistory) {
        try {
            Map<String, Object> ragResult = nlpService.askRag(message, language, userRole, conversationHistory);
            String answer = (String) ragResult.get("answer");
            if (isValidAnswer(answer)) {
                List<String> sources = (List<String>) ragResult.get("sources");
                if (sources != null && !sources.isEmpty()) {
                    String sourceName = sources.get(0)
                            .replace("knowledge_base/", "")
                            .replace(".txt", "")
                            .replace("_", " ");
                    answer += "\n\n📄 Source: " + sourceName;
                }
            }
            return answer;
        } catch (Exception e) {
            System.err.println("RAG call failed: " + e.getMessage());
            return null;
        }
    }

    private List<Map<String, String>> buildConversationHistory(Long userId) {
        try {
            List<ChatMessage> recent = chatMessageRepository.findRecentByUserId(
                    userId, PageRequest.of(0, 10));
            Collections.reverse(recent); // oldest first for chronological context
            List<Map<String, String>> history = new ArrayList<>();
            for (ChatMessage msg : recent) {
                Map<String, String> human = new HashMap<>();
                human.put("role", "user");
                human.put("content", msg.getUserMessage());
                history.add(human);

                Map<String, String> assistant = new HashMap<>();
                assistant.put("role", "assistant");
                assistant.put("content", msg.getBotResponse());
                history.add(assistant);
            }
            return history;
        } catch (Exception e) {
            System.err.println("Could not load conversation history: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isValidAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty()) return false;
        String lower = answer.toLowerCase();
        return !lower.contains("indisponible")
                && !lower.contains("erreur")
                && !lower.contains("error")
                && !lower.contains("aucune base")
                && !lower.contains("non configurée")
                && !lower.contains("service nlp")
                && !lower.contains("service rag")
                && !lower.contains("service ia")
                && !lower.contains("temporarily unavailable")
                && !lower.contains("external ai service")
                && !lower.contains("غير متاحة")
                && !lower.contains("غير متاح حالياً");
    }

    private String sanitizeResponse(String response, Map<String, Object> nlpResult, String language) {
        if (!isValidAnswer(response)) {
            String nlpResponse = (String) nlpResult.get("response");
            if (isValidAnswer(nlpResponse)) return nlpResponse;
            return getGenericFallbackResponse(language);
        }
        return response;
    }

    private String getGenericFallbackResponse(String language) {
        if ("ar".equals(language)) {
            return "💡 أنا **FPST SmartDesk**، المساعد الذكي ! اسألني !\n\n📄 وثائق | 📅 برنامج | 🎓 تخصصات | 📝 تسجيل\n💰 منح | 💼 تربصات | 📜 قانون | 🔧 دعم\n\nقلي شنو تحب ! 😊";
        }
        if ("en".equals(language)) {
            return "💡 I'm **FPST SmartDesk**, your AI assistant! Ask me anything!\n\nI can help with:\n📄 Documents | 📅 Schedule | 🎓 Programs | 📝 Registration\n💰 Scholarships | 💼 Internships | 📜 Rules | 🔧 Support\n\nWhat do you need? 😊";
        }
        return "💡 Je suis **FPST SmartDesk**, votre assistant IA ! Posez-moi votre question !\n\nJe peux vous aider avec :\n📄 Documents | 📅 Planning | 🎓 Filières | 📝 Inscription\n💰 Bourses | 💼 Stages | 📜 Règlement | 🔧 Support\n\nDites-moi de quoi avez-vous besoin ! 😊";
    }

    public List<ChatMessage> getHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return chatMessageRepository.findByUserIdOrderByCreatedAtAsc(user.getId());
    }
}
