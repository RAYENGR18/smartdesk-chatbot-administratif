package com.chatbot.backend.controller;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.EmploiDuTemps;
import com.chatbot.backend.model.Salle;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.UserRepository;
import com.chatbot.backend.security.AuthenticatedUser;
import com.chatbot.backend.security.CurrentUser;
import com.chatbot.backend.service.PlanningService;
import com.chatbot.backend.util.Niveaux;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planningService;
    private final UserRepository userRepository;

    // ============================================
    // MON EMPLOI DU TEMPS (déterminé côté serveur)
    //  - étudiant   : SA filière et SON niveau, lus en base (jamais envoyés par le client)
    //  - professeur : ses propres créneaux
    // ============================================
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> monPlanning() {
        AuthenticatedUser me = CurrentUser.get();
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> ApiException.unauthorized("Compte introuvable."));

        Map<String, Object> res = new LinkedHashMap<>();
        if (me.isProfesseur()) {
            res.put("type", "PROFESSEUR");
            res.put("creneaux", planningService.getPlanningByProfesseur(user.getId()));
            return ResponseEntity.ok(res);
        }
        if (!me.isEtudiant()) {
            throw ApiException.badRequest("Utilisez la vue par filière de l'espace administrateur.");
        }

        res.put("type", "ETUDIANT");
        if (user.getFiliere() == null || user.getNiveau() == null) {
            res.put("filiere", null);
            res.put("niveau", null);
            res.put("creneaux", List.of());
            res.put("message", "Votre filière ou votre niveau n'est pas renseigné. Contactez l'administration.");
            return ResponseEntity.ok(res);
        }
        Map<String, Object> filiere = new LinkedHashMap<>();
        filiere.put("id", user.getFiliere().getId());
        filiere.put("code", user.getFiliere().getCode());
        filiere.put("nom", user.getFiliere().getNom());
        res.put("filiere", filiere);
        res.put("niveau", user.getNiveau());
        res.put("niveauLibelle", Niveaux.libelle(user.getNiveau()));
        res.put("creneaux", planningService.getPlanningByFiliereEtNiveau(user.getFiliere().getId(), user.getNiveau()));
        return ResponseEntity.ok(res);
    }

    // PLANNING PAR FILIÈRE (admin, professeurs) — ?niveau=L1 pour un seul niveau
    @GetMapping("/filiere/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSEUR')")
    public ResponseEntity<List<EmploiDuTemps>> getPlanningByFiliere(@PathVariable Long id,
                                                                    @RequestParam(required = false) String niveau) {
        String n = niveau == null || niveau.isBlank() ? null
                : Niveaux.normaliser(niveau).orElseThrow(() -> ApiException.badRequest("Niveau invalide."));
        return ResponseEntity.ok(planningService.getPlanningByFiliereEtNiveau(id, n));
    }

    // PLANNING PAR PROFESSEUR (soi-même ou admin)
    @GetMapping("/professeur/{id}")
    public ResponseEntity<List<EmploiDuTemps>> getPlanningByProfesseur(@PathVariable Long id) {
        CurrentUser.requireSelfOrAdmin(id);
        return ResponseEntity.ok(planningService.getPlanningByProfesseur(id));
    }

    // PLANNING PAR SALLE (admin)
    @GetMapping("/salle/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EmploiDuTemps>> getPlanningBySalle(@PathVariable Long id) {
        return ResponseEntity.ok(planningService.getPlanningBySalle(id));
    }

    // TOUS LES CRÉNEAUX (admin)
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EmploiDuTemps>> getAllCreneaux() {
        return ResponseEntity.ok(planningService.getAllCreneaux());
    }

    // AJOUTER UN CRÉNEAU (admin) — le niveau est obligatoire
    @PostMapping("/creneaux")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> ajouterCreneau(@RequestBody Map<String, Object> request) {
        try {
            EmploiDuTemps.Jour jour = EmploiDuTemps.Jour.valueOf((String) request.get("jour"));
            LocalTime heureDebut = LocalTime.parse((String) request.get("heureDebut"));
            LocalTime heureFin = LocalTime.parse((String) request.get("heureFin"));
            Long matiereId = Long.valueOf(request.get("matiereId").toString());
            Long salleId = Long.valueOf(request.get("salleId").toString());
            Long filiereId = Long.valueOf(request.get("filiereId").toString());
            String niveau = Niveaux.normaliser(request.get("niveau") == null ? null : request.get("niveau").toString())
                    .orElseThrow(() -> new RuntimeException("Le niveau (L1, L2, L3, M1, M2...) est obligatoire"));
            Object prof = request.get("professeurId");
            Long professeurId = prof != null && !prof.toString().isBlank() ? Long.valueOf(prof.toString()) : null;
            String groupe = (String) request.get("groupe");
            String semestre = (String) request.get("semestre");

            EmploiDuTemps creneau = planningService.creerCreneau(
                    jour, heureDebut, heureFin, matiereId, salleId, filiereId, niveau,
                    professeurId, groupe, semestre);
            return ResponseEntity.ok(creneau);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage() != null ? e.getMessage() : "Données du créneau invalides");
            error.put("message", error.get("error"));
            return ResponseEntity.badRequest().body(error);
        }
    }

    // SUPPRIMER UN CRÉNEAU (admin)
    @DeleteMapping("/creneaux/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> supprimerCreneau(@PathVariable Long id) {
        planningService.supprimerCreneau(id);
        return ResponseEntity.ok(Map.of("message", "Créneau supprimé avec succès"));
    }

    // GÉNÉRATION AUTOMATIQUE (admin) : une filière + un niveau
    @PostMapping("/generer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> genererPlanning(@RequestBody Map<String, Object> request) {
        try {
            Long filiereId = Long.valueOf(request.get("filiereId").toString());
            String niveau = Niveaux.normaliser(request.get("niveau") == null ? null : request.get("niveau").toString())
                    .orElseThrow(() -> new RuntimeException("Choisissez le niveau à générer (L1, L2, L3, M1, M2...)"));
            String semestre = (String) request.getOrDefault("semestre", "S1");

            List<EmploiDuTemps> planning = planningService.genererPlanningAutomatique(filiereId, niveau, semestre);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Planning généré avec succès");
            response.put("nbCreneaux", planning.size());
            response.put("planning", planning);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage() != null ? e.getMessage() : "Génération impossible");
            error.put("message", error.get("error"));
            return ResponseEntity.badRequest().body(error);
        }
    }

    // SALLES DISPONIBLES POUR UN CRÉNEAU (admin)
    @GetMapping("/salles-disponibles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Salle>> getSallesDisponibles(@RequestParam String jour,
                                                            @RequestParam String heureDebut,
                                                            @RequestParam String heureFin) {
        EmploiDuTemps.Jour jourEnum = EmploiDuTemps.Jour.valueOf(jour);
        return ResponseEntity.ok(planningService.getSallesDisponibles(
                jourEnum, LocalTime.parse(heureDebut), LocalTime.parse(heureFin)));
    }
}
