package com.chatbot.backend.fiche;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.DocumentRequest;
import com.chatbot.backend.model.EmploiDuTemps;
import com.chatbot.backend.model.Filiere;
import com.chatbot.backend.model.Matiere;
import com.chatbot.backend.model.Note;
import com.chatbot.backend.model.User;
import com.chatbot.backend.presence.PresenceService;
import com.chatbot.backend.registre.PersonneEtablissement;
import com.chatbot.backend.registre.RegistreEtablissementRepository;
import com.chatbot.backend.registre.RegistreSource;
import com.chatbot.backend.registre.TypePersonne;
import com.chatbot.backend.repository.ChatMessageRepository;
import com.chatbot.backend.repository.DocumentRepository;
import com.chatbot.backend.repository.NoteRepository;
import com.chatbot.backend.repository.UserRepository;
import com.chatbot.backend.service.NoteService;
import com.chatbot.backend.service.PlanningService;
import com.chatbot.backend.util.Niveaux;
import com.chatbot.backend.util.TextUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Fiche complète d'un étudiant ou d'un professeur, pour l'administration :
 * identité (CIN...), registre, scolarité, notes et moyennes, emploi du temps,
 * documents, badge et présences. Lecture seule.
 */
@Service
public class FicheService {

    private final UserRepository users;
    private final NoteRepository notes;
    private final NoteService noteService;
    private final PlanningService planning;
    private final DocumentRepository documents;
    private final ChatMessageRepository messages;
    private final PresenceService presences;
    private final RegistreSource registreSource;
    private final RegistreEtablissementRepository registreLocal;
    private final ZoneId zone;

    public FicheService(UserRepository users, NoteRepository notes, NoteService noteService,
                        PlanningService planning, DocumentRepository documents, ChatMessageRepository messages,
                        PresenceService presences, RegistreSource registreSource,
                        RegistreEtablissementRepository registreLocal,
                        @Value("${app.timezone:Africa/Tunis}") String timezone) {
        this.users = users;
        this.notes = notes;
        this.noteService = noteService;
        this.planning = planning;
        this.documents = documents;
        this.messages = messages;
        this.presences = presences;
        this.registreSource = registreSource;
        this.registreLocal = registreLocal;
        this.zone = ZoneId.of(timezone);
    }

    // ── Liste ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> rechercher(String type, String q, Long filiereId, String niveau, int page, int taille) {
        User.Role role = "PROFESSEUR".equalsIgnoreCase(type) ? User.Role.PROFESSEUR : User.Role.USER;
        String texte = q == null ? "" : q.trim().toLowerCase();
        String niv = niveau == null ? "" : niveau.trim();
        int t = Math.max(1, Math.min(taille, 100));
        Page<User> res = users.rechercherFiches(role, filiereId, niv, texte,
                PageRequest.of(Math.max(page, 0), t, Sort.by("lastName", "firstName", "id")));

        List<Map<String, Object>> lignes = new ArrayList<>();
        for (User u : res.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nom", u.getLastName());
            m.put("prenom", u.getFirstName());
            m.put("cin", u.getCin());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            if (role == User.Role.USER) {
                m.put("filiere", u.getFiliere() != null ? u.getFiliere().getCode() : null);
                m.put("niveau", u.getNiveau());
                m.put("matricule", u.getStudentId());
                Bilan b = bilan(notes.findByEtudiantId(u.getId()));
                m.put("moyenneGenerale", b.moyenneGenerale());
                m.put("nbNotes", b.nbNotes());
            } else {
                m.put("departement", u.getDepartment());
                m.put("nbMatieres", u.getMatieresEnseignees() == null ? 0 : u.getMatieresEnseignees().size());
                m.put("badge", u.getRfidUid() != null);
            }
            lignes.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contenu", lignes);
        out.put("total", res.getTotalElements());
        out.put("page", res.getNumber());
        out.put("pages", res.getTotalPages());
        return out;
    }

    // ── Fiche ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> fiche(Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur introuvable."));
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", switch (u.getRole()) {
            case USER -> "ETUDIANT";
            case PROFESSEUR -> "PROFESSEUR";
            case ADMIN -> "ADMIN";
        });
        f.put("identite", identite(u));
        f.put("registre", registre(u));
        f.put("documents", documents(u));
        f.put("chatbot", chatbot(u));

        if (u.getRole() == User.Role.USER) {
            f.put("scolarite", scolarite(u));
            List<Note> sesNotes = notes.findByEtudiantId(u.getId());
            f.put("notes", noteService.getNotesView(u.getId()));
            Bilan b = bilan(sesNotes);
            f.put("resultats", b.parMatiere());
            f.put("moyenneGenerale", b.moyenneGenerale());
            f.put("modulesValides", b.modulesValides());
            f.put("modules", b.parMatiere().size());
            f.put("emploiDuTemps", u.getFiliere() != null && u.getNiveau() != null
                    ? creneaux(planning.getPlanningByFiliereEtNiveau(u.getFiliere().getId(), u.getNiveau()), false)
                    : List.of());
        } else if (u.getRole() == User.Role.PROFESSEUR) {
            f.put("enseignement", enseignement(u));
            f.put("emploiDuTemps", creneaux(planning.getPlanningByProfesseur(u.getId()), true));
            f.put("presence", presence(u));
        }
        return f;
    }

    private Map<String, Object> identite(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("cin", u.getCin());
        m.put("nom", u.getLastName());
        m.put("prenom", u.getFirstName());
        m.put("username", u.getUsername());
        m.put("email", u.getEmail());
        m.put("telephone", u.getPhone());
        m.put("adresse", u.getAddress());
        m.put("genre", u.getGender());
        m.put("nationalite", u.getNationality());
        m.put("dateNaissance", u.getBirthDate());
        m.put("lieuNaissance", u.getBirthPlace());
        m.put("pere", u.getFatherName());
        m.put("mere", u.getMotherName());
        m.put("bac", u.getBacType());
        m.put("bourse", u.getScholarship());
        m.put("etablissement", u.getInstitution());
        m.put("faculte", u.getFaculty());
        m.put("departement", u.getDepartment());
        m.put("specialite", u.getSpecialite());
        m.put("anneeUniversitaire", u.getAcademicYear());
        m.put("compteCreeLe", u.getCreatedAt());
        return m;
    }

    private Map<String, Object> registre(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", registreSource.mode());
        if (u.getCin() == null || u.getCin().isBlank()) {
            m.put("present", false);
            return m;
        }
        Optional<PersonneEtablissement> p;
        try {
            p = registreSource.trouverParCin(u.getCin());
        } catch (RuntimeException e) {
            m.put("present", null);
            m.put("erreur", "Base de l'établissement injoignable.");
            return m;
        }
        m.put("present", p.isPresent());
        p.ifPresent(r -> {
            m.put("type", r.type() == TypePersonne.PROFESSEUR ? "PROFESSEUR" : "ETUDIANT");
            m.put("nom", r.nom());
            m.put("prenom", r.prenom());
            m.put("filiere", r.filiere());
            m.put("niveau", r.niveau());
            m.put("matricule", r.matricule());
            m.put("email", r.email());
            m.put("departement", r.departement());
            boolean identiteOk = TextUtils.normaliserNom(r.nom()).equals(TextUtils.normaliserNom(u.getLastName()))
                    && TextUtils.normaliserNom(r.prenom()).equals(TextUtils.normaliserNom(u.getFirstName()));
            boolean scolariteOk = u.getRole() != User.Role.USER
                    || (u.getFiliere() != null && r.filiere() != null
                        && (r.filiere().equalsIgnoreCase(u.getFiliere().getCode())
                            || TextUtils.normaliserNom(r.filiere()).equals(TextUtils.normaliserNom(u.getFiliere().getNom())))
                        && Objects.equals(Niveaux.normaliser(r.niveau()).orElse(r.niveau()), u.getNiveau()));
            m.put("coherent", identiteOk && scolariteOk);
        });
        registreLocal.findByCin(u.getCin()).ifPresent(r -> {
            m.put("origine", r.getOrigine());
            m.put("miseAJour", r.getDateMiseAJour());
        });
        return m;
    }

    private Map<String, Object> scolarite(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        Filiere f = u.getFiliere();
        m.put("filiere", f == null ? null : Map.of("id", f.getId(), "code", f.getCode(), "nom", f.getNom()));
        m.put("niveau", u.getNiveau());
        m.put("niveauLibelle", Niveaux.libelle(u.getNiveau()));
        m.put("matricule", u.getStudentId());
        m.put("anneeUniversitaire", u.getAcademicYear());
        return m;
    }

    private Map<String, Object> enseignement(User u) {
        List<Map<String, Object>> mat = new ArrayList<>();
        u.getMatieresEnseignees().stream()
                .sorted(Comparator.comparing(Matiere::getCode, Comparator.nullsLast(String::compareTo)))
                .forEach(x -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", x.getId());
                    m.put("code", x.getCode());
                    m.put("nom", x.getNom());
                    m.put("niveau", x.getNiveau());
                    m.put("filiere", x.getFiliere() != null ? x.getFiliere().getCode() : null);
                    m.put("volumeHoraire", x.getVolumeHoraire());
                    mat.add(m);
                });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matieres", mat);
        m.put("filieres", u.getFilieresProfesseur().stream().map(Filiere::getCode).sorted().toList());
        m.put("notesSaisies", notes.countByProfesseurId(u.getId()));
        return m;
    }

    private Map<String, Object> presence(User u) {
        LocalDate fin = LocalDate.now(zone);
        LocalDate debut = fin.minusDays(29);
        List<PresenceService.PresenceView> liste = presences.rechercher(debut, fin, u.getId());
        long minutes = liste.stream().map(PresenceService.PresenceView::dureeMinutes)
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("badge", u.getRfidUid());
        m.put("periodeDebut", debut);
        m.put("periodeFin", fin);
        m.put("joursPresents", liste.size());
        m.put("heuresTotales", String.format("%dh%02d", minutes / 60, minutes % 60));
        m.put("departsNonPointes", liste.stream().filter(p -> "DEPART_NON_POINTE".equals(p.statut())).count());
        m.put("historique", liste.stream()
                .sorted(Comparator.comparing(PresenceService.PresenceView::date).reversed()).toList());
        return m;
    }

    private List<Map<String, Object>> creneaux(List<EmploiDuTemps> liste, boolean avecGroupe) {
        List<Map<String, Object>> out = new ArrayList<>();
        liste.stream()
                .sorted(Comparator.comparing((EmploiDuTemps e) -> e.getJour().ordinal())
                        .thenComparing(EmploiDuTemps::getHeureDebut))
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("jour", e.getJour().name());
                    m.put("heureDebut", e.getHeureDebut().toString());
                    m.put("heureFin", e.getHeureFin().toString());
                    m.put("matiere", e.getMatiere() != null ? e.getMatiere().getNom() : null);
                    m.put("salle", e.getSalle() != null ? e.getSalle().getNom() : null);
                    m.put("professeur", e.getProfesseur() != null
                            ? e.getProfesseur().getFirstName() + " " + e.getProfesseur().getLastName() : null);
                    if (avecGroupe) {
                        m.put("groupe", (e.getFiliere() != null ? e.getFiliere().getCode() : "")
                                + (e.getNiveau() != null ? " " + e.getNiveau() : ""));
                    }
                    out.add(m);
                });
        return out;
    }

    private List<Map<String, Object>> documents(User u) {
        List<Map<String, Object>> out = new ArrayList<>();
        documents.findByUserId(u.getId()).stream()
                .sorted(Comparator.comparing(DocumentRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", d.getId());
                    m.put("type", d.getDocumentType());
                    m.put("format", d.getFormat());
                    m.put("statut", d.getStatus());
                    m.put("date", d.getCreatedAt());
                    out.add(m);
                });
        return out;
    }

    private Map<String, Object> chatbot(User u) {
        // Nombre et date seulement : le contenu des conversations n'est pas affiché sur la fiche
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nbMessages", messages.countByUserId(u.getId()));
        m.put("dernierMessage", messages.findDernierMessage(u.getId()));
        return m;
    }

    // ── Moyennes ──────────────────────────────────────────────────────

    /** Résultat par matière : notes par type d'évaluation, moyenne et validation (moyenne ≥ 10). */
    record Bilan(List<Map<String, Object>> parMatiere, Double moyenneGenerale, int modulesValides, int nbNotes) {
    }

    static Bilan bilan(List<Note> sesNotes) {
        Map<String, List<Note>> parMatiere = new LinkedHashMap<>();
        sesNotes.stream()
                .sorted(Comparator.comparing(FicheService::nomMatiere, String.CASE_INSENSITIVE_ORDER))
                .forEach(n -> parMatiere.computeIfAbsent(nomMatiere(n), k -> new ArrayList<>()).add(n));

        List<Map<String, Object>> lignes = new ArrayList<>();
        double somme = 0, coefs = 0;
        int valides = 0;
        for (Map.Entry<String, List<Note>> e : parMatiere.entrySet()) {
            List<Note> ns = e.getValue();
            Matiere mat = ns.stream().map(Note::getMatiere).filter(Objects::nonNull).findFirst().orElse(null);
            double coef = mat != null && mat.getCoefficient() != null && mat.getCoefficient() > 0 ? mat.getCoefficient() : 1.0;

            Map<String, Double> parType = new LinkedHashMap<>();
            ns.stream().sorted(Comparator.comparing(n -> Objects.toString(n.getTypeEvaluation(), "")))
                    .forEach(n -> parType.put(Objects.toString(n.getTypeEvaluation(), "Note"), n.getValeur()));
            // Moyenne de la matière : celle enregistrée avec les notes, sinon la moyenne simple
            Double moyenne = ns.stream().map(Note::getMoyenne).filter(Objects::nonNull).findFirst()
                    .orElseGet(() -> ns.stream().map(Note::getValeur).filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null));
            if (moyenne != null) {
                moyenne = arrondi(moyenne);
                somme += moyenne * coef;
                coefs += coef;
                if (moyenne >= 10) valides++;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("matiere", e.getKey());
            m.put("code", mat != null ? mat.getCode() : null);
            m.put("coefficient", coef);
            m.put("notes", parType);
            m.put("moyenne", moyenne);
            m.put("valide", moyenne != null && moyenne >= 10);
            lignes.add(m);
        }
        Double generale = coefs > 0 ? arrondi(somme / coefs) : null;
        return new Bilan(lignes, generale, valides, sesNotes.size());
    }

    private static String nomMatiere(Note n) {
        if (n.getNomMatiere() != null && !n.getNomMatiere().isBlank()) return n.getNomMatiere();
        return n.getMatiere() != null ? n.getMatiere().getNom() : "—";
    }

    private static double arrondi(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
