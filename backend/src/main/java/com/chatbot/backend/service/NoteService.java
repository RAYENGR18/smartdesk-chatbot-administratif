package com.chatbot.backend.service;

import com.chatbot.backend.model.Note;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.MatiereRepository;
import com.chatbot.backend.repository.NoteRepository;
import com.chatbot.backend.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    // ================================================================
    // CONSULTATION DES NOTES
    // ================================================================

    public List<Note> getNotesByEtudiant(Long etudiantId) {
        return noteRepository.findByEtudiantId(etudiantId);
    }

    public List<Note> getAllNotes() {
        return noteRepository.findAll();
    }

    public void supprimerNote(Long id) {
        noteRepository.deleteById(id);
    }

    // ================================================================
    // IMPORT DEPUIS FICHIER (EXCEL .xlsx ou CSV .csv)
    // ================================================================

    /**
     * Importe les notes depuis un fichier Excel ou CSV.
     * Identification de l'étudiant par le champ CIN.
     *
     * Colonnes attendues (en-tête sur la 1ère ligne) :
     *   cin | nomMatiere | typeEvaluation | note | moyenne | commentaire
     *
     * "moyenne" et "commentaire" sont optionnels.
     *
     * Mode : AJOUT (les notes existantes de l'étudiant ne sont PAS supprimées,
     *         on met à jour si déjà présent pour la même matière + typeEvaluation,
     *         sinon on insère).
     */
    @Transactional
    public Map<String, Object> importerNotes(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new IllegalArgumentException("Fichier invalide.");

        List<Map<String, String>> rows;

        if (filename.toLowerCase().endsWith(".xlsx")) {
            rows = parseExcel(file);
        } else if (filename.toLowerCase().endsWith(".csv")) {
            rows = parseCsv(file);
        } else {
            throw new IllegalArgumentException("Format non supporté. Utilisez .xlsx ou .csv");
        }

        int inserted = 0;
        int updated  = 0;
        int skipped  = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int lineNum = i + 2; // +2 : ligne 1 = en-tête

            String cin = clean(row.get("cin"));
            if (cin.isEmpty()) {
                errors.add("Ligne " + lineNum + " : CIN manquant.");
                skipped++;
                continue;
            }

            Optional<User> etudiantOpt = userRepository.findByCinIgnoreCase(cin);
            if (etudiantOpt.isEmpty()) {
                errors.add("Ligne " + lineNum + " : Étudiant introuvable pour CIN = " + cin);
                skipped++;
                continue;
            }
            User etudiant = etudiantOpt.get();

            String nomMatiere     = clean(row.get("nomMatiere"));
            String typeEvaluationRaw = clean(row.get("typeEvaluation"));
            final String typeEvaluation = typeEvaluationRaw.isEmpty() ? "Examen" : typeEvaluationRaw;
            final String finalNomMatiere = nomMatiere;

            String noteStr    = clean(row.get("note"));
            String moyenneStr = clean(row.get("moyenne"));
            String commentaire = clean(row.get("commentaire"));

            if (noteStr.isEmpty()) {
                errors.add("Ligne " + lineNum + " : Note manquante pour CIN = " + cin);
                skipped++;
                continue;
            }

            double valeur;
            try {
                valeur = Double.parseDouble(noteStr.replace(",", "."));
                if (valeur < 0 || valeur > 20)
                    throw new NumberFormatException();
            } catch (NumberFormatException e) {
                errors.add("Ligne " + lineNum + " : Note invalide (" + noteStr + ") pour CIN = " + cin);
                skipped++;
                continue;
            }

            Double moyenneVal = null;
            if (!moyenneStr.isEmpty()) {
                try {
                    moyenneVal = Double.parseDouble(moyenneStr.replace(",", "."));
                } catch (NumberFormatException ignored) {}
            }

            // Résoudre la matière (optionnel — on stocke le nom brut si non trouvée)
            Optional<com.chatbot.backend.model.Matiere> matiereOpt = finalNomMatiere.isEmpty()
                    ? Optional.empty()
                    : matiereRepository.findByNom(finalNomMatiere);

            // Chercher note existante (même étudiant + même matière + même type)
            List<Note> existing = noteRepository.findByEtudiantId(etudiant.getId())
                    .stream()
                    .filter(n -> typeEvaluation.equalsIgnoreCase(n.getTypeEvaluation())
                            && finalNomMatiere.equalsIgnoreCase(n.getNomMatiere() != null ? n.getNomMatiere()
                               : (n.getMatiere() != null ? n.getMatiere().getNom() : "")))
                    .toList();

            Note note;
            if (!existing.isEmpty()) {
                note = existing.get(0);
                updated++;
            } else {
                note = new Note();
                note.setEtudiant(etudiant);
                note.setSource("IMPORT");
                inserted++;
            }

            note.setValeur(valeur);
            note.setMoyenne(moyenneVal);
            note.setTypeEvaluation(typeEvaluation);
            note.setNomMatiere(finalNomMatiere.isEmpty() ? null : finalNomMatiere);
            note.setCommentaire(commentaire.isEmpty() ? null : commentaire);
            matiereOpt.ifPresent(m -> note.setMatiere((com.chatbot.backend.model.Matiere) m));

            noteRepository.save(note);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLignes",  rows.size());
        result.put("inserted",     inserted);
        result.put("updated",      updated);
        result.put("skipped",      skipped);
        result.put("errors",       errors);
        return result;
    }

    // ================================================================
    // PARSEURS
    // ================================================================

    private List<Map<String, String>> parseExcel(MultipartFile file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rowIt = sheet.iterator();
            if (!rowIt.hasNext()) return rows;

            // Lire l'en-tête
            Row headerRow = rowIt.next();
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cellToString(cell).trim().toLowerCase());
            }

            while (rowIt.hasNext()) {
                Row row = rowIt.next();
                // Ignorer les lignes totalement vides
                boolean empty = true;
                for (Cell c : row) {
                    if (c != null && c.getCellType() != CellType.BLANK) { empty = false; break; }
                }
                if (empty) continue;

                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    map.put(headers.get(i), cellToString(cell).trim());
                }
                rows.add(map);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) return rows;

            // Détecter le séparateur (virgule ou point-virgule)
            String sep = headerLine.contains(";") ? ";" : ",";
            String[] headers = headerLine.split(sep, -1);
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim().toLowerCase().replace("\"", "");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = line.split(sep, -1);
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String val = i < values.length ? values[i].trim().replace("\"", "") : "";
                    map.put(headers[i], val);
                }
                rows.add(map);
            }
        }
        return rows;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                // Éviter d'afficher "15.0" pour un entier
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default      -> "";
        };
    }

    private String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
