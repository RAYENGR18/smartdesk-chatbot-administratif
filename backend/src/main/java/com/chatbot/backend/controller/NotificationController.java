package com.chatbot.backend.controller;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.Notification;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.NotificationRepository;
import com.chatbot.backend.repository.UserRepository;
import com.chatbot.backend.security.AuthenticatedUser;
import com.chatbot.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repo;
    private final UserRepository userRepository;

    // ── Admin : liste
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Notification> getAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    // ── Admin : créer une notification
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Notification> create(@RequestBody Notification notif) {
        if (notif.getTitle() == null || notif.getTitle().isBlank() || notif.getTitle().length() > 200) {
            throw ApiException.badRequest("Le titre est obligatoire (200 caractères maximum).");
        }
        if (notif.getContent() == null || notif.getContent().isBlank() || notif.getContent().length() > 5000) {
            throw ApiException.badRequest("Le contenu est obligatoire (5000 caractères maximum).");
        }
        if (notif.getTarget() == null) {
            throw ApiException.badRequest("La cible (STUDENT ou PROFESSOR) est obligatoire.");
        }
        if (notif.getTargetIds() == null || notif.getTargetIds().isBlank()) {
            notif.setTargetIds("ALL");
        }
        if (notif.getPriority() == null) {
            notif.setPriority(Notification.NotifPriority.INFO);
        }
        notif.setId(null);
        notif.setCreatedBy(CurrentUser.get().username());
        return ResponseEntity.ok(repo.save(notif));
    }

    // ── Admin : activer / désactiver
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Notification> toggle(@PathVariable Long id) {
        Notification n = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification non trouvée."));
        n.setActive(!n.isActive());
        return ResponseEntity.ok(repo.save(n));
    }

    // ── Admin : supprimer
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            throw ApiException.notFound("Notification non trouvée.");
        }
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Notification supprimée"));
    }

    // ── Étudiant : notifications de SA filière (le paramètre n'est utilisé que par l'admin)
    @GetMapping("/student")
    public List<Notification> forStudent(@RequestParam(required = false) String fieldOfStudy) {
        AuthenticatedUser me = CurrentUser.get();
        String field = fieldOfStudy;
        if (!me.isAdmin()) {
            field = userRepository.findById(me.id()).map(User::getFieldOfStudy).orElse(null);
        }
        final String target = field == null ? "" : field.trim();
        return repo.findByTargetAndActiveOrderByCreatedAtDesc(Notification.NotifTarget.STUDENT, true)
                .stream()
                .filter(n -> matchesTarget(n.getTargetIds(), target))
                .toList();
    }

    // ── Professeur : SES notifications (le paramètre n'est utilisé que par l'admin)
    @GetMapping("/professor")
    public List<Notification> forProfessor(@RequestParam(required = false) String userId) {
        AuthenticatedUser me = CurrentUser.get();
        String id = me.isAdmin() && userId != null ? userId.trim() : String.valueOf(me.id());
        return repo.findByTargetAndActiveOrderByCreatedAtDesc(Notification.NotifTarget.PROFESSOR, true)
                .stream()
                .filter(n -> matchesTarget(n.getTargetIds(), id))
                .toList();
    }

    // ── Stats (admin)
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> stats() {
        long total = repo.count();
        long students = repo.findByTargetAndActiveOrderByCreatedAtDesc(Notification.NotifTarget.STUDENT, true).size();
        long profs = repo.findByTargetAndActiveOrderByCreatedAtDesc(Notification.NotifTarget.PROFESSOR, true).size();
        return Map.of("total", total, "activeForStudents", students, "activeForProfessors", profs);
    }

    /**
     * "ALL" ou liste séparée par des virgules, comparée élément par élément
     * (l'ancien LIKE '%1%' envoyait aussi les notifications du professeur 12 au professeur 1).
     */
    private static boolean matchesTarget(String targetIds, String value) {
        if (targetIds == null || targetIds.isBlank() || "ALL".equalsIgnoreCase(targetIds.trim())) {
            return true;
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        return Arrays.stream(targetIds.split(","))
                .map(String::trim)
                .anyMatch(t -> Objects.equals(t.toLowerCase(), value.toLowerCase()));
    }
}
