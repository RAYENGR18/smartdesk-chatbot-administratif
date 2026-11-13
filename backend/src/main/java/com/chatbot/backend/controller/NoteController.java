package com.chatbot.backend.controller;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.Note;
import com.chatbot.backend.security.CurrentUser;
import com.chatbot.backend.service.NoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private static final Logger log = LoggerFactory.getLogger(NoteController.class);

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    // ── Mes notes : l'étudiant est identifié par son token, jamais par un paramètre ──
    @GetMapping("/me")
    public ResponseEntity<List<NoteService.NoteView>> getMesNotes() {
        return ResponseEntity.ok(noteService.getNotesView(CurrentUser.get().id()));
    }

    // ── Consultation par id : soi-même ou admin ──────────────────────
    @GetMapping("/etudiant/{id}")
    public ResponseEntity<List<NoteService.NoteView>> getNotesEtudiant(@PathVariable Long id) {
        CurrentUser.requireSelfOrAdmin(id);
        return ResponseEntity.ok(noteService.getNotesView(id));
    }

    // ── Consultation admin (toutes les notes) ─────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Note>> getAllNotes() {
        return ResponseEntity.ok(noteService.getAllNotes());
    }

    // ── Suppression (admin) ───────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> supprimerNote(@PathVariable Long id) {
        noteService.supprimerNote(id);
        return ResponseEntity.ok(Map.of("message", "Note supprimée"));
    }

    // ── Import fichier Excel ou CSV (admin) ───────────────────────────
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importerNotes(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Le fichier est vide.");
        }
        try {
            return ResponseEntity.ok(noteService.importerNotes(file));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Échec de l'import des notes", e);
            throw ApiException.badRequest("Import impossible : " + e.getClass().getSimpleName()
                    + ". Vérifiez le fichier (colonnes CIN, Matière, Note).");
        }
    }
}
