package com.chatbot.backend.service;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.Matiere;
import com.chatbot.backend.model.Note;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.MatiereRepository;
import com.chatbot.backend.repository.NoteRepository;
import com.chatbot.backend.repository.UserRepository;
import com.chatbot.backend.util.TabularFileReader;
import com.chatbot.backend.util.TabularFileReader.Ligne;
import com.chatbot.backend.util.TextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final MatiereRepository matiereRepository;

    public NoteService(NoteRepository noteRepository,
                       UserRepository userRepository,
                       MatiereRepository matiereRepository) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.matiereRepository = matiereRepository;
    }

    /** Note telle qu'affichée à l'étudiant : aucune donnée sur d'autres personnes. */
    public record NoteView(Long id, String nomMatiere, String codeMatiere, String typeEvaluation,
                           Double valeur, Double moyenne, String commentaire, LocalDateTime dateSaisie) {
    }

    // ================================================================
    // CONSULTATION
    // ================================================================

    public List<NoteView> getNotesView(Long etudiantId) {
        return noteRepository.findByEtudiantId(etudiantId).stream()
                .sorted(Comparator.comparing((Note n) -> nomMatiere(n), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(n -> Objects.toString(n.getTypeEvaluation(), "")))
                .map(n -> new NoteView(n.getId(), nomMatiere(n),
                        n.getMatiere() != null ? n.getMatiere().getCode() : null,
                        n.getTypeEvaluation(), n.getValeur(), n.getMoyenne(), n.getCommentaire(), n.getDateSaisie()))
                .toList();
    }

    public List<Note> getAllNotes() {
        return noteRepository.findAll();
    }

    public void supprimerNote(Long id) {
        if (!noteRepository.existsById(id)) {
            throw ApiException.notFound("Note introuvable.");
        }
        noteRepository.deleteById(id);
    }

    private static String nomMatiere(Note n) {
        if (n.getNomMatiere() != null && !n.getNomMatiere().isBlank()) return n.getNomMatiere();
        return n.getMatiere() != null ? n.getMatiere().getNom() : "—";
    }

    // ================================================================
    // IMPORT EXCEL (.xlsx) / CSV
    // ================================================================

    /**
     * Importe les notes : chaque ligne est rattachée à l'étudiant par son CIN.
     *
     * Colonnes (en-têtes libres, accents/casse ignorés) :
     *   cin* | matiere* | note* | type (Examen par défaut : DS, TP, Projet...) | moyenne | commentaire
     *
     * Une note existante (même étudiant + même matière + même type) est mise à jour,
     * sinon elle est ajoutée. Les lignes en erreur sont ignorées et listées dans le rapport.
     */
    @Transactional
    public Map<String, Object> importerNotes(MultipartFile file) {
        List<Ligne> lignes = TabularFileReader.lire(file);
        if (lignes.isEmpty()) {
            throw ApiException.badRequest("Le fichier ne contient aucune ligne de notes.");
        }
        verifierColonnes(lignes.get(0));

        int inserted = 0, updated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        Map<String, Optional<User>> etudiants = new HashMap<>();
        Map<String, Optional<Matiere>> matieres = new HashMap<>();

        for (Ligne l : lignes) {
            String prefixe = "Ligne " + l.numero() + " : ";
            String cinBrut = l.get("cin", "numerocin", "carteidentite", "cinetudiant", "identifiant");
            if (cinBrut.isEmpty()) cinBrut = l.chercher("cin");
            String cin = TextUtils.normaliserCin(cinBrut);
            if (cin.isEmpty()) {
                errors.add(prefixe + "CIN manquant.");
                skipped++;
                continue;
            }

            Optional<User> etudiantOpt = etudiants.computeIfAbsent(cin, c ->
                    userRepository.findByCinIgnoreCase(c).filter(u -> u.getRole() == User.Role.USER));
            if (etudiantOpt.isEmpty()) {
                errors.add(prefixe + "aucun compte étudiant avec le CIN " + cin
                        + " (l'étudiant doit d'abord créer son compte).");
                skipped++;
                continue;
            }
            User etudiant = etudiantOpt.get();

            String matiereLue = l.get("matiere", "nommatiere", "module", "unite", "codematiere");
            if (matiereLue.isEmpty()) matiereLue = l.chercher("matiere", "module");
            final String nomMatiere = matiereLue;
            if (nomMatiere.isEmpty()) {
                errors.add(prefixe + "matière manquante (CIN " + cin + ").");
                skipped++;
                continue;
            }

            String noteStr = l.get("note", "valeur", "notefinale", "noteobtenue", "resultat");
            if (noteStr.isEmpty()) noteStr = l.chercher("note");
            Double valeur = lireNote(noteStr);
            if (valeur == null) {
                errors.add(prefixe + "note invalide « " + noteStr + " » (attendu : nombre entre 0 et 20).");
                skipped++;
                continue;
            }
            String moyenneStr = l.get("moyenne", "moyennematiere");
            Double moyenne = moyenneStr.isEmpty() ? null : lireNote(moyenneStr);
            if (!moyenneStr.isEmpty() && moyenne == null) {
                errors.add(prefixe + "moyenne invalide « " + moyenneStr + " » (ligne importée sans moyenne).");
            }

            String type = normaliserType(l.get("type", "typeevaluation", "evaluation", "epreuve"));
            String commentaire = l.get("commentaire", "remarque", "observation");

            Optional<Matiere> matiere = matieres.computeIfAbsent(TextUtils.normaliserNom(nomMatiere),
                    k -> trouverMatiere(nomMatiere));
            String nomAffiche = matiere.map(Matiere::getNom).orElse(nomMatiere.trim());

            Note note = noteRepository.findByEtudiantId(etudiant.getId()).stream()
                    .filter(n -> type.equalsIgnoreCase(n.getTypeEvaluation())
                            && TextUtils.normaliserNom(nomMatiere(n)).equals(TextUtils.normaliserNom(nomAffiche)))
                    .findFirst()
                    .orElse(null);
            if (note == null) {
                note = new Note();
                note.setEtudiant(etudiant);
                note.setSource("IMPORT");
                inserted++;
            } else {
                updated++;
            }
            note.setNomMatiere(limiter(nomAffiche, 255));
            note.setMatiere(matiere.orElse(null));
            note.setTypeEvaluation(type);
            note.setValeur(valeur);
            note.setMoyenne(moyenne);
            note.setCommentaire(commentaire.isEmpty() ? null : limiter(commentaire, 255));
            noteRepository.save(note);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLignes", lignes.size());
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("errors", errors.size() > 200 ? errors.subList(0, 200) : errors);
        return result;
    }

    private static void verifierColonnes(Ligne premiere) {
        Set<String> cols = premiere.valeurs().keySet();
        boolean cin = cols.stream().anyMatch(c -> c.contains("cin") || c.equals("identifiant") || c.contains("carteidentite"));
        boolean matiere = cols.stream().anyMatch(c -> c.contains("matiere") || c.equals("module") || c.equals("unite"));
        boolean note = cols.stream().anyMatch(c -> c.startsWith("note") || c.equals("valeur") || c.equals("resultat"));
        if (!cin || !matiere || !note) {
            throw ApiException.badRequest("Colonnes obligatoires manquantes. La première ligne doit contenir "
                    + "au moins : CIN, Matière, Note (colonnes trouvées : " + String.join(", ", cols) + ").");
        }
    }

    private Optional<Matiere> trouverMatiere(String saisie) {
        String cle = TextUtils.normaliserNom(saisie);
        return matiereRepository.findAll().stream()
                .filter(m -> TextUtils.normaliserNom(m.getNom()).equals(cle)
                        || TextUtils.normaliserNom(m.getCode()).equals(cle))
                .findFirst();
    }

    private static Double lireNote(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            double v = Double.parseDouble(s.trim().replace(",", ".").replace("/20", "").trim());
            return v >= 0 && v <= 20 ? Math.round(v * 100) / 100.0 : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Uniformise les types usuels pour l'affichage (badges DS / Examen / TP / Projet). */
    private static String normaliserType(String s) {
        String v = TextUtils.normaliserEntete(s);
        if (v.isEmpty() || v.startsWith("exam") || v.equals("ex") || v.equals("final")) return "Examen";
        if (v.startsWith("ds") || v.startsWith("cc") || v.startsWith("controle") || v.startsWith("devoir")) return "DS";
        if (v.startsWith("tp") || v.startsWith("td")) return "TP";
        if (v.startsWith("projet") || v.startsWith("mini")) return "Projet";
        if (v.startsWith("ratt")) return "Rattrapage";
        return limiter(s.trim(), 50);
    }

    private static String limiter(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
