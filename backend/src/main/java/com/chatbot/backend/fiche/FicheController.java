package com.chatbot.backend.fiche;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Fiches des étudiants et des professeurs (administration uniquement). */
@RestController
@RequestMapping("/api/admin/fiches")
@PreAuthorize("hasRole('ADMIN')")
public class FicheController {

    private final FicheService service;

    public FicheController(FicheService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> rechercher(@RequestParam(defaultValue = "ETUDIANT") String type,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) Long filiereId,
                                                          @RequestParam(required = false) String niveau,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "25") int taille) {
        return ResponseEntity.ok(service.rechercher(type, q, filiereId, niveau, page, taille));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> fiche(@PathVariable Long id) {
        return ResponseEntity.ok(service.fiche(id));
    }
}
