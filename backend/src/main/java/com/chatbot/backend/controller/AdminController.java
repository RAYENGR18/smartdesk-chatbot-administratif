package com.chatbot.backend.controller;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.*;
import com.chatbot.backend.repository.*;
import com.chatbot.backend.security.CurrentUser;
import com.chatbot.backend.service.DatabaseResetService;
import com.chatbot.backend.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Espace administrateur. Toutes les routes exigent le rôle ADMIN
 * (règle d'URL dans SecurityConfig + @PreAuthorize par sécurité).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final FiliereRepository filiereRepository;
    private final SalleRepository salleRepository;
    private final MatiereRepository matiereRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final DatabaseResetService resetService;
    private final UserAdminService userAdminService;

    // STATISTIQUES GÉNÉRALES
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalMessages", chatMessageRepository.count());
        stats.put("totalDocuments", documentRepository.count());
        stats.put("totalFilieres", filiereRepository.count());
        stats.put("totalSalles", salleRepository.count());
        stats.put("totalMatieres", matiereRepository.count());
        stats.put("totalCreneaux", emploiDuTempsRepository.count());
        stats.put("nbEtudiants", userRepository.countByRole(User.Role.USER));
        stats.put("nbProfesseurs", userRepository.countByRole(User.Role.PROFESSEUR));
        stats.put("nbAdmins", userRepository.countByRole(User.Role.ADMIN));
        return ResponseEntity.ok(stats);
    }

    // LISTE DES UTILISATEURS (le mot de passe n'est jamais sérialisé : @JsonIgnore)
    @GetMapping("/users")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    // REMISE À ZÉRO (confirmation obligatoire : ?confirm=RESET)
    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, String>> resetDatabase(@RequestParam(required = false) String confirm) {
        if (!"RESET".equals(confirm)) {
            throw ApiException.badRequest("Confirmation requise : ajoutez ?confirm=RESET.");
        }
        resetService.resetAll();
        return ResponseEntity.ok(Map.of("status", "Données effacées (comptes administrateurs conservés)"));
    }

    // SUPPRIMER UN UTILISATEUR
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        userAdminService.deleteUser(id, CurrentUser.get());
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé avec succès"));
    }

    // CHANGER LE RÔLE D'UN UTILISATEUR
    @PutMapping("/users/{id}/role")
    public ResponseEntity<Map<String, String>> changeRole(@PathVariable Long id,
                                                          @RequestBody Map<String, String> request) {
        User user = userAdminService.changeRole(id, request.get("role"), CurrentUser.get());
        return ResponseEntity.ok(Map.of("message", "Rôle mis à jour : " + user.getRole().name()));
    }

    // AFFECTER LES FILIÈRES D'UN PROFESSEUR
    @PutMapping("/users/{id}/filieres")
    public ResponseEntity<Map<String, String>> updateFilieresProfesseur(@PathVariable Long id,
                                                                        @RequestBody List<Long> filiereIds) {
        User user = findUser(id);
        user.setFilieresProfesseur(new HashSet<>(filiereRepository.findAllById(filiereIds)));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Filières du professeur mises à jour"));
    }

    // AFFECTER LES MATIÈRES D'UN PROFESSEUR
    @PutMapping("/users/{id}/matieres")
    public ResponseEntity<Map<String, String>> updateMatieresEnseignees(@PathVariable Long id,
                                                                        @RequestBody List<Long> matiereIds) {
        User user = findUser(id);
        user.setMatieresEnseignees(new HashSet<>(matiereRepository.findAllById(matiereIds)));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Matières du professeur mises à jour"));
    }

    // MODIFIER LA SPÉCIALITÉ D'UN PROFESSEUR
    @PutMapping("/users/{id}/specialite")
    public ResponseEntity<Map<String, String>> updateSpecialite(@PathVariable Long id,
                                                                @RequestBody Map<String, String> request) {
        String specialite = request.get("specialite");
        if (specialite != null && specialite.length() > 255) {
            throw ApiException.badRequest("Spécialité trop longue (255 caractères maximum).");
        }
        User user = findUser(id);
        user.setSpecialite(specialite);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Spécialité mise à jour"));
    }

    // LISTE DES CONVERSATIONS
    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessage>> getMessages() {
        return ResponseEntity.ok(chatMessageRepository.findAll());
    }

    // SUPPRIMER UN MESSAGE
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Map<String, String>> deleteMessage(@PathVariable Long id) {
        if (!chatMessageRepository.existsById(id)) {
            throw ApiException.notFound("Message non trouvé.");
        }
        chatMessageRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Message supprimé avec succès"));
    }

    // LISTE DES DOCUMENTS
    @GetMapping("/documents")
    public ResponseEntity<List<DocumentRequest>> getDocuments() {
        return ResponseEntity.ok(documentRepository.findAll());
    }

    // LISTE DES PROFESSEURS
    @GetMapping("/professeurs")
    public ResponseEntity<List<User>> getProfesseurs() {
        return ResponseEntity.ok(userRepository.findByRole(User.Role.PROFESSEUR));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilisateur non trouvé."));
    }
}
