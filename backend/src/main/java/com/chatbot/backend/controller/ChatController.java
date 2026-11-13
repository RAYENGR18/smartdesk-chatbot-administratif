package com.chatbot.backend.controller;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.ChatMessage;
import com.chatbot.backend.security.AuthenticatedUser;
import com.chatbot.backend.security.CurrentUser;
import com.chatbot.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final Set<String> LANGUAGES = Set.of("fr", "en", "ar");

    private final ChatService chatService;

    // ENVOYER UN MESSAGE — l'expéditeur est TOUJOURS l'utilisateur du token
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> request) {
        AuthenticatedUser me = CurrentUser.get();

        Object rawMessage = request.get("message");
        String message = rawMessage instanceof String s ? s.trim() : "";
        if (message.isEmpty()) {
            throw ApiException.badRequest("Le message est vide.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw ApiException.badRequest("Message trop long (" + MAX_MESSAGE_LENGTH + " caractères maximum).");
        }

        Object rawLanguage = request.get("language");
        String language = rawLanguage instanceof String l && LANGUAGES.contains(l) ? l : null;

        // Le contexte envoyé par le client est ignoré : il est reconstruit côté serveur
        ChatMessage chatMsg = chatService.sendMessage(message, me.username(), language);

        Map<String, Object> response = new HashMap<>();
        response.put("answer", chatMsg.getBotResponse());
        response.put("response", chatMsg.getBotResponse());
        response.put("intention", chatMsg.getIntention());
        response.put("language", chatMsg.getLanguage());
        response.put("id", chatMsg.getId());
        return ResponseEntity.ok(response);
    }

    // HISTORIQUE (soi-même ou admin)
    @GetMapping("/history/{username}")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String username) {
        CurrentUser.requireSelfOrAdmin(username);
        return ResponseEntity.ok(chatService.getHistory(username));
    }
}
