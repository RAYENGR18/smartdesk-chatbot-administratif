package com.chatbot.backend.controller;

import com.chatbot.backend.model.Matiere;
import com.chatbot.backend.model.Filiere;
import com.chatbot.backend.repository.MatiereRepository;
import com.chatbot.backend.repository.FiliereRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/matieres")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:61748"})
@RequiredArgsConstructor
public class MatiereController {

    private final MatiereRepository matiereRepository;
    private final FiliereRepository filiereRepository;

    @GetMapping
    public ResponseEntity<List<Matiere>> getAll() {
        return ResponseEntity.ok(matiereRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Matiere> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                matiereRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Matiere non trouvee"))
        );
    }

    @GetMapping("/filiere/{filiereId}")
    public ResponseEntity<List<Matiere>> getByFiliere(@PathVariable Long filiereId) {
        // Search both legacy and many-to-many relationships
        return ResponseEntity.ok(matiereRepository.findByAnyFiliereId(filiereId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request) {
        try {
            String code = (String) request.get("code");
            if (matiereRepository.existsByCode(code)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Le code matiere existe deja");
                return ResponseEntity.badRequest().body(error);
            }

            Matiere matiere = new Matiere();
            matiere.setNom((String) request.get("nom"));
            matiere.setCode(code);
            matiere.setCoefficient(request.get("coefficient") != null ?
                    Double.valueOf(request.get("coefficient").toString()) : null);
            matiere.setVolumeHoraire(request.get("volumeHoraire") != null ?
                    Integer.valueOf(request.get("volumeHoraire").toString()) : null);
            if (request.get("type") != null) {
                matiere.setType(Matiere.TypeMatiere.valueOf((String) request.get("type")));
            }

            // Handle multiple filieres (many-to-many)
            if (request.get("filiereIds") != null) {
                @SuppressWarnings("unchecked")
                List<Object> filiereIds = (List<Object>) request.get("filiereIds");
                Set<Filiere> filieres = new HashSet<>();
                for (Object fId : filiereIds) {
                    Long filiereId = Long.valueOf(fId.toString());
                    Filiere filiere = filiereRepository.findById(filiereId)
                            .orElseThrow(() -> new RuntimeException("Filiere non trouvee: " + filiereId));
                    filieres.add(filiere);
                }
                matiere.setFilieres(filieres);
                // Also set legacy filiere to first one for backward compatibility
                if (!filieres.isEmpty()) {
                    matiere.setFiliere(filieres.iterator().next());
                }
            }
            // Backward compatibility: single filiereId
            else if (request.get("filiereId") != null) {
                Long filiereId = Long.valueOf(request.get("filiereId").toString());
                Filiere filiere = filiereRepository.findById(filiereId)
                        .orElseThrow(() -> new RuntimeException("Filiere non trouvee"));
                matiere.setFiliere(filiere);
                matiere.getFilieres().add(filiere);
            }

            return ResponseEntity.ok(matiereRepository.save(matiere));
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Matiere existing = matiereRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Matiere non trouvee"));

            if (request.containsKey("nom")) existing.setNom((String) request.get("nom"));
            if (request.containsKey("code")) existing.setCode((String) request.get("code"));
            if (request.containsKey("coefficient"))
                existing.setCoefficient(Double.valueOf(request.get("coefficient").toString()));
            if (request.containsKey("volumeHoraire"))
                existing.setVolumeHoraire(Integer.valueOf(request.get("volumeHoraire").toString()));
            if (request.containsKey("type"))
                existing.setType(Matiere.TypeMatiere.valueOf((String) request.get("type")));

            // Handle multiple filieres
            if (request.containsKey("filiereIds")) {
                @SuppressWarnings("unchecked")
                List<Object> filiereIds = (List<Object>) request.get("filiereIds");
                Set<Filiere> filieres = new HashSet<>();
                for (Object fId : filiereIds) {
                    Long filiereId = Long.valueOf(fId.toString());
                    Filiere filiere = filiereRepository.findById(filiereId)
                            .orElseThrow(() -> new RuntimeException("Filiere non trouvee: " + filiereId));
                    filieres.add(filiere);
                }
                existing.setFilieres(filieres);
                if (!filieres.isEmpty()) {
                    existing.setFiliere(filieres.iterator().next());
                }
            }
            // Backward compatibility
            else if (request.containsKey("filiereId")) {
                Long filiereId = Long.valueOf(request.get("filiereId").toString());
                Filiere filiere = filiereRepository.findById(filiereId)
                        .orElseThrow(() -> new RuntimeException("Filiere non trouvee"));
                existing.setFiliere(filiere);
                existing.getFilieres().clear();
                existing.getFilieres().add(filiere);
            }

            return ResponseEntity.ok(matiereRepository.save(existing));
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        matiereRepository.deleteById(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Matiere supprimee avec succes");
        return ResponseEntity.ok(response);
    }
}
