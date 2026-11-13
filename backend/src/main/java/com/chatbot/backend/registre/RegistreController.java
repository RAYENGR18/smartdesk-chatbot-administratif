package com.chatbot.backend.registre;

import com.chatbot.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Registre de l'établissement (admin) : import, consultation, données de démonstration. */
@RestController
@RequestMapping("/api/admin/registre")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RegistreController {

    private final RegistreService registreService;
    private final DemoDataService demoDataService;
    private final RegistreSource registreSource;

    @GetMapping
    public Page<Map<String, Object>> rechercher(@RequestParam(defaultValue = "") String q,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size) {
        return registreService.rechercher(q, type, page, size);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return registreService.statistiques(registreSource.mode());
    }

    @PostMapping("/import")
    public ResponseEntity<RegistreService.ResultatImport> importer(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Le fichier est vide.");
        }
        return ResponseEntity.ok(registreService.importer(file));
    }

    @PostMapping("/demo")
    public ResponseEntity<Map<String, Object>> chargerDemo() throws Exception {
        return ResponseEntity.ok(demoDataService.charger());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> supprimer(@PathVariable Long id) {
        registreService.supprimer(id);
        return ResponseEntity.ok(Map.of("message", "Entrée supprimée du registre"));
    }
}
