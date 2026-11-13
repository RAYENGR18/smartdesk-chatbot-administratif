package com.chatbot.backend.service;

import com.chatbot.backend.model.*;
import com.chatbot.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanningService {

    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final SalleRepository salleRepository;
    private final MatiereRepository matiereRepository;
    private final FiliereRepository filiereRepository;
    private final UserRepository userRepository;

    // ============================================
    // RÉCUPÉRER LE PLANNING PAR FILIÈRE
    // ============================================
    public List<EmploiDuTemps> getPlanningByFiliere(Long filiereId) {
        return emploiDuTempsRepository.findByFiliereIdOrderByJourAscHeureDebutAsc(filiereId);
    }

    // ============================================
    // RÉCUPÉRER LE PLANNING PAR PROFESSEUR
    // ============================================
    public List<EmploiDuTemps> getPlanningByProfesseur(Long professeurId) {
        return emploiDuTempsRepository.findByProfesseurIdOrderByJourAscHeureDebutAsc(professeurId);
    }

    // ============================================
    // RÉCUPÉRER LE PLANNING PAR SALLE
    // ============================================
    public List<EmploiDuTemps> getPlanningBySalle(Long salleId) {
        return emploiDuTempsRepository.findBySalleIdOrderByJourAscHeureDebutAsc(salleId);
    }

    // ============================================
    // RÉCUPÉRER LE PLANNING PAR JOUR ET FILIÈRE
    // ============================================
    public List<EmploiDuTemps> getPlanningByJourAndFiliere(EmploiDuTemps.Jour jour, Long filiereId) {
        return emploiDuTempsRepository.findByJourAndFiliereId(jour, filiereId);
    }

    // ============================================
    // AJOUTER UN CRÉNEAU (AVEC VÉRIFICATION CONFLITS)
    // ============================================
    public EmploiDuTemps ajouterCreneau(EmploiDuTemps creneau) {
        // Vérifier conflit de salle
        List<EmploiDuTemps> conflitsSalle = emploiDuTempsRepository.findConflitsSalle(
                creneau.getSalle().getId(),
                creneau.getJour(),
                creneau.getHeureDebut(),
                creneau.getHeureFin()
        );
        if (!conflitsSalle.isEmpty()) {
            throw new RuntimeException("Conflit : la salle " + creneau.getSalle().getNom() +
                    " est déjà occupée à cet horaire le " + creneau.getJour());
        }

        // Vérifier conflit de professeur
        if (creneau.getProfesseur() != null) {
            List<EmploiDuTemps> conflitsProf = emploiDuTempsRepository.findConflitsProfesseur(
                    creneau.getProfesseur().getId(),
                    creneau.getJour(),
                    creneau.getHeureDebut(),
                    creneau.getHeureFin()
            );
            if (!conflitsProf.isEmpty()) {
                throw new RuntimeException("Conflit : le professeur est déjà affecté à un autre cours à cet horaire");
            }
        }

        return emploiDuTempsRepository.save(creneau);
    }

    // ============================================
    // CRÉER UN CRÉNEAU À PARTIR DES IDs
    // ============================================
    public EmploiDuTemps creerCreneau(
            EmploiDuTemps.Jour jour,
            LocalTime heureDebut,
            LocalTime heureFin,
            Long matiereId,
            Long salleId,
            Long filiereId,
            Long professeurId,
            String groupe,
            String semestre
    ) {
        Matiere matiere = matiereRepository.findById(matiereId)
                .orElseThrow(() -> new RuntimeException("Matière non trouvée"));
        Salle salle = salleRepository.findById(salleId)
                .orElseThrow(() -> new RuntimeException("Salle non trouvée"));
        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() -> new RuntimeException("Filière non trouvée"));

        User professeur = null;
        if (professeurId != null) {
            professeur = userRepository.findById(professeurId)
                    .orElseThrow(() -> new RuntimeException("Professeur non trouvé"));
        }

        EmploiDuTemps creneau = new EmploiDuTemps();
        creneau.setJour(jour);
        creneau.setHeureDebut(heureDebut);
        creneau.setHeureFin(heureFin);
        creneau.setMatiere(matiere);
        creneau.setSalle(salle);
        creneau.setFiliere(filiere);
        creneau.setProfesseur(professeur);
        creneau.setGroupe(groupe);
        creneau.setSemestre(semestre);

        return ajouterCreneau(creneau);
    }

    // ============================================
    // SUPPRIMER UN CRÉNEAU
    // ============================================
    public void supprimerCreneau(Long id) {
        if (!emploiDuTempsRepository.existsById(id)) {
            throw new RuntimeException("Créneau non trouvé");
        }
        emploiDuTempsRepository.deleteById(id);
    }

    // ============================================
    // GÉNÉRATION AUTOMATIQUE DES PLANNINGS
    // ============================================
    // Semaines par semestre (pour convertir volume horaire total → créneaux hebdomadaires)
    private static final int SEMAINES_PAR_SEMESTRE = 15;
    // Maximum de créneaux par jour par professeur
    private static final int MAX_CRENEAUX_PAR_PROF_PAR_JOUR = 2;
    // Maximum de créneaux par semaine par professeur
    private static final int MAX_CRENEAUX_PAR_PROF_PAR_SEMAINE = 8;

    @Transactional
    public List<EmploiDuTemps> genererPlanningAutomatique(Long filiereId, String semestre) {
        filiereRepository.findById(filiereId)
                .orElseThrow(() -> new RuntimeException("Filière non trouvée"));

        // Supprimer l'ancien planning de cette filière avant de régénérer
        emploiDuTempsRepository.deleteByFiliereId(filiereId);

        List<Matiere> matieres = matiereRepository.findByAnyFiliereId(filiereId);
        List<Salle> sallesDisponibles = salleRepository.findByDisponible(true);

        if (matieres.isEmpty()) throw new RuntimeException("Aucune matière pour cette filière");
        if (sallesDisponibles.isEmpty()) throw new RuntimeException("Aucune salle disponible");

        EmploiDuTemps.Jour[] jours = {
                EmploiDuTemps.Jour.LUNDI, EmploiDuTemps.Jour.MARDI,
                EmploiDuTemps.Jour.MERCREDI, EmploiDuTemps.Jour.JEUDI,
                EmploiDuTemps.Jour.VENDREDI, EmploiDuTemps.Jour.SAMEDI
        };
        LocalTime[] heuresDebut = {
                LocalTime.of(8, 30), LocalTime.of(10, 15), LocalTime.of(12, 0),
                LocalTime.of(13, 45), LocalTime.of(15, 30)
        };

        // Profs affectés à cette filière (ou tous si aucun n'est configuré)
        List<User> tousProfesseurs = userRepository.findByRole(User.Role.PROFESSEUR);
        List<User> professeurs = tousProfesseurs.stream()
                .filter(p -> p.getFilieresProfesseur() == null
                          || p.getFilieresProfesseur().isEmpty()
                          || p.getFilieresProfesseur().stream().anyMatch(f -> f.getId().equals(filiereId)))
                .collect(Collectors.toList());
        if (professeurs.isEmpty()) professeurs = tousProfesseurs;

        // Charge totale par prof (nombre de créneaux cette semaine)
        Map<Long, Integer> chargeProf = new HashMap<>();
        // Charge par prof par jour (pour éviter >2 cours/jour)
        Map<Long, Map<EmploiDuTemps.Jour, Integer>> chargeJour = new HashMap<>();
        for (User p : professeurs) {
            chargeProf.put(p.getId(), 0);
            chargeJour.put(p.getId(), new HashMap<>());
        }

        List<EmploiDuTemps> planning = new ArrayList<>();

        // Calculer combien de créneaux hebdomadaires par matière
        // volumeHoraire = total semestre → diviser par nb semaines et par 1.5h/créneau (max 2/semaine)
        List<Matiere> slots = new ArrayList<>();
        for (Matiere m : matieres) {
            int nbCreneaux = 1;
            if (m.getVolumeHoraire() != null && m.getVolumeHoraire() > 0) {
                double heuresParSemaine = (double) m.getVolumeHoraire() / SEMAINES_PAR_SEMESTRE;
                nbCreneaux = Math.min(2, Math.max(1, (int) Math.ceil(heuresParSemaine / 1.5)));
            }
            for (int i = 0; i < nbCreneaux; i++) slots.add(m);
        }

        // slotsParJour[j] = nombre de cours déjà placés pour le jour j
        int[] slotsParJour = new int[jours.length];
        int salleIdx = 0;
        int jourDepart = 0; // jour de départ pour le prochain slot (tourne pour équilibrer)

        for (Matiere matiere : slots) {
            boolean placed = false;

            // Essayer chaque jour en commençant par jourDepart (round-robin sur les jours)
            for (int tentJour = 0; tentJour < jours.length && !placed; tentJour++) {
                int dayIdx = (jourDepart + tentJour) % jours.length;
                EmploiDuTemps.Jour jour = jours[dayIdx];

                if (slotsParJour[dayIdx] >= heuresDebut.length) continue; // jour plein

                LocalTime debut = heuresDebut[slotsParJour[dayIdx]];
                LocalTime fin = debut.plusMinutes(90);
                Salle salle = sallesDisponibles.get(salleIdx % sallesDisponibles.size());

                User profChoisi = trouverProfDisponible(matiere, professeurs, chargeProf, chargeJour, jour);
                if (profChoisi == null) continue; // aucun prof dispo ce jour

                try {
                    EmploiDuTemps creneau = creerCreneau(
                            jour, debut, fin,
                            matiere.getId(), salle.getId(), filiereId,
                            profChoisi.getId(), "Groupe A", semestre
                    );
                    planning.add(creneau);
                    placed = true;

                    chargeProf.merge(profChoisi.getId(), 1, Integer::sum);
                    chargeJour.get(profChoisi.getId()).merge(jour, 1, Integer::sum);
                    slotsParJour[dayIdx]++;
                    salleIdx++;
                    // Le prochain slot commencera sur le jour suivant (distribution équilibrée)
                    jourDepart = (dayIdx + 1) % jours.length;
                } catch (RuntimeException e) {
                    salleIdx++; // conflit salle → essayer autre salle au prochain tour
                }
            }
        }

        return planning;
    }

    // ============================================
    // MATCHING PROF ↔ MATIÈRE — avec limites journalières et hebdomadaires
    // ============================================
    private User trouverProfDisponible(
            Matiere matiere,
            List<User> professeurs,
            Map<Long, Integer> chargeProf,
            Map<Long, Map<EmploiDuTemps.Jour, Integer>> chargeJour,
            EmploiDuTemps.Jour jour
    ) {
        if (professeurs.isEmpty()) return null;

        // Filtrer : exclure les profs qui ont atteint leur limite journalière ou hebdomadaire
        List<User> disponibles = professeurs.stream()
                .filter(p -> {
                    int totalSemaine = chargeProf.getOrDefault(p.getId(), 0);
                    int nbCeJour = chargeJour.getOrDefault(p.getId(), new HashMap<>())
                                             .getOrDefault(jour, 0);
                    return totalSemaine < MAX_CRENEAUX_PAR_PROF_PAR_SEMAINE
                        && nbCeJour < MAX_CRENEAUX_PAR_PROF_PAR_JOUR;
                })
                .collect(Collectors.toList());

        if (disponibles.isEmpty()) return null;

        // Priorité 1 : profs directement assignés à cette matière
        List<User> affectes = disponibles.stream()
                .filter(p -> p.getMatieresEnseignees() != null &&
                             p.getMatieresEnseignees().stream()
                              .anyMatch(m -> m.getId().equals(matiere.getId())))
                .collect(Collectors.toList());

        if (!affectes.isEmpty()) {
            return affectes.stream()
                    .min(Comparator.comparingInt((User p) -> chargeProf.getOrDefault(p.getId(), 0))
                                   .thenComparingLong(User::getId))
                    .orElse(null);
        }

        // Priorité 2 : correspondance par spécialité (fallback si aucune matière assignée)
        String nomMatiere = normaliserTexte(matiere.getNom());
        List<User> specialistes = new ArrayList<>();
        for (User prof : disponibles) {
            if (prof.getSpecialite() != null && !prof.getSpecialite().isBlank()) {
                String[] mots = normaliserTexte(prof.getSpecialite()).split("[,;\\s]+");
                for (String mot : mots) {
                    if (mot.length() >= 3 && nomMatiere.contains(mot)) {
                        specialistes.add(prof);
                        break;
                    }
                }
            }
        }

        // Priorité 3 : n'importe quel prof disponible (dernier recours)
        List<User> candidats = specialistes.isEmpty() ? disponibles : specialistes;

        return candidats.stream()
                .min(Comparator.comparingInt((User p) -> chargeProf.getOrDefault(p.getId(), 0))
                               .thenComparingLong(User::getId))
                .orElse(null);
    }

    private String normaliserTexte(String texte) {
        if (texte == null) return "";
        return texte.toLowerCase()
                .replaceAll("[àáâä]", "a")
                .replaceAll("[éèêë]", "e")
                .replaceAll("[îïí]", "i")
                .replaceAll("[ôö]", "o")
                .replaceAll("[ùûü]", "u")
                .replaceAll("[ç]", "c")
                .replaceAll("[ñ]", "n");
    }

    // ============================================
    // OBTENIR TOUS LES CRÉNEAUX
    // ============================================
    public List<EmploiDuTemps> getAllCreneaux() {
        return emploiDuTempsRepository.findAll();
    }

    // ============================================
    // SALLES DISPONIBLES POUR UN CRÉNEAU
    // ============================================
    public List<Salle> getSallesDisponibles(EmploiDuTemps.Jour jour, LocalTime heureDebut, LocalTime heureFin) {
        List<Salle> toutesLesSalles = salleRepository.findByDisponible(true);
        return toutesLesSalles.stream()
                .filter(salle -> {
                    List<EmploiDuTemps> conflits = emploiDuTempsRepository.findConflitsSalle(
                            salle.getId(), jour, heureDebut, heureFin
                    );
                    return conflits.isEmpty();
                })
                .collect(Collectors.toList());
    }
}
