package com.chatbot.backend.controller;

import com.chatbot.backend.model.Note;
import com.chatbot.backend.service.NoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:61748"})
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    // ── Consultation étudiant ─────────────────────────────────────────
    @GetMapping("/etudiant/{id}")
    public ResponseEntity<List<Note>> getNotesEtudiant(@PathVariable Long id) {
        return ResponseEntity.ok(noteService.getNotesByEtudiant(id));
    }

    // ── Consultation admin (toutes les notes) ─────────────────────────
    @GetMapping
    public ResponseEntity<List<Note>> getAllNotes() {
        return ResponseEntity.ok(noteService.getAllNotes());
    }

    // ── Suppression (admin) ───────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> supprimerNote(@PathVariable Long id) {
        noteService.supprimerNote(id);
        return ResponseEntity.ok().build();
    }

    // ── Import fichier Excel ou CSV (admin) ───────────────────────────
    @PostMapping("/import")
    public ResponseEntity<?> importerNotes(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "Le fichier est vide.");
            return ResponseEntity.badRequest().body(err);
        }
        try {
            Map<String, Object> result = noteService.importerNotes(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "Erreur lors de l'import : " + e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
