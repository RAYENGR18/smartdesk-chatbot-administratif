package com.chatbot.backend.controller;

import com.chatbot.backend.model.Notification;
import com.chatbot.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:61748"})
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repo;

    // ── Admin : liste
    @GetMapping
    public List<Notification> getAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    // ── Admin : créer une notification
    @PostMapping
    public ResponseEntity<Notification> create(@RequestBody Notification notif) {
        if (notif.getTargetIds() == null || notif.getTargetIds().isBlank())
            notif.setTargetIds("ALL");
        return ResponseEntity.ok(repo.save(notif));
    }

    // ── Admin : activer / désactiver
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        return repo.findById(id).map(n -> {
            n.setActive(!n.isActive());
            return ResponseEntity.ok(repo.save(n));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Admin : supprimer ──────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Notification supprimée"));
    }

    // ── Étudiant : ses notifications par filière ──────────────────
    @GetMapping("/student")
    public List<Notification> forStudent(@RequestParam String fieldOfStudy) {
        return repo.findActiveForStudent(fieldOfStudy);
    }

    // ── Professeur : ses notifications par userId ─────────────────
    @GetMapping("/professor")
    public List<Notification> forProfessor(@RequestParam String userId) {
        return repo.findActiveForProfessor(userId);
    }

    // ── Stats pour admin chatbot ───────────────────────────────────
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long total    = repo.count();
        long students = repo.findByTargetAndActiveOrderByCreatedAtDesc(
                            Notification.NotifTarget.STUDENT, true).size();
        long profs    = repo.findByTargetAndActiveOrderByCreatedAtDesc(
                            Notification.NotifTarget.PROFESSOR, true).size();
        return Map.of(
            "total", total,
            "activeForStudents", students,
            "activeForProfessors", profs
        );
    }
}
