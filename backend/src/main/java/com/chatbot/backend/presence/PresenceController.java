package com.chatbot.backend.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Présence des professeurs (espace administrateur). */
@RestController
@RequestMapping("/api/admin/presences")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping
    public List<PresenceService.PresenceView> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Long professeurId) {
        return presenceService.rechercher(debut, fin, professeurId);
    }

    @GetMapping("/resume")
    public Map<String, Object> resume() {
        return presenceService.resumeDuJour();
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Long professeurId) {
        String csv = presenceService.exporterCsv(debut, fin, professeurId);
        String nom = "presences_" + (debut != null ? debut : LocalDate.now()) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/badges")
    public List<Map<String, Object>> badges() {
        return presenceService.badges();
    }

    @PutMapping("/badges/{professeurId}")
    public Map<String, String> attribuerBadge(@PathVariable Long professeurId,
                                              @RequestBody Map<String, String> body) {
        presenceService.attribuerBadge(professeurId, body.get("uid"));
        return Map.of("message", body.get("uid") == null || body.get("uid").isBlank()
                ? "Badge retiré" : "Badge attribué");
    }

    @GetMapping("/badges-inconnus")
    public List<Map<String, Object>> badgesInconnus() {
        return presenceService.badgesInconnus();
    }

    @GetMapping("/passages")
    public List<Map<String, Object>> derniersPassages() {
        return presenceService.derniersPassages();
    }

    @PostMapping("/simuler/{professeurId}")
    public PresenceService.ResultatScan simuler(@PathVariable Long professeurId) {
        return presenceService.simuler(professeurId);
    }
}
