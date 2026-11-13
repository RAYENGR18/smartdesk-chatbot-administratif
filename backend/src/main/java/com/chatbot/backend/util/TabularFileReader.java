package com.chatbot.backend.util;

import com.chatbot.backend.exception.ApiException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lecture d'un fichier Excel (.xlsx) ou CSV en lignes "en-tête normalisé -> valeur".
 *
 * Gère les pièges courants des fichiers produits par Excel :
 *  - en-têtes avec accents, majuscules, espaces ("Nom de la matière" -> "nomdelamatiere") ;
 *  - CSV avec BOM UTF-8, ou encodé en Windows-1252 (Excel français) ;
 *  - séparateur ";" (Excel FR) ou "," ; champs entre guillemets ;
 *  - cellules numériques (15 -> "15", 12.5 -> "12.5") et formules (valeur calculée).
 */
public final class TabularFileReader {

    public static final int MAX_LIGNES = 20_000;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private TabularFileReader() {
    }

    /** Une ligne lue : numéro de ligne dans le fichier (1 = en-tête) et valeurs par colonne. */
    public record Ligne(int numero, Map<String, String> valeurs) {

        /** Première valeur non vide parmi plusieurs noms de colonnes possibles. */
        public String get(String... alias) {
            for (String a : alias) {
                String v = valeurs.get(TextUtils.normaliserEntete(a));
                if (v != null && !v.isBlank()) return v.trim();
            }
            return "";
        }

        /** Repli : première colonne dont le nom CONTIENT un des fragments ("nomdelamatiere" contient "matiere"). */
        public String chercher(String... fragments) {
            for (String f : fragments) {
                String frag = TextUtils.normaliserEntete(f);
                for (Map.Entry<String, String> e : valeurs.entrySet()) {
                    if (e.getKey().contains(frag) && e.getValue() != null && !e.getValue().isBlank()) {
                        return e.getValue().trim();
                    }
                }
            }
            return "";
        }
    }

    public static List<Ligne> lire(MultipartFile file) {
        try {
            return lire(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw ApiException.badRequest("Fichier illisible.");
        }
    }

    public static List<Ligne> lire(String nomFichier, byte[] contenu) {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT);
        try {
            if (nom.endsWith(".xlsx")) return lireExcel(new ByteArrayInputStream(contenu));
            if (nom.endsWith(".csv") || nom.endsWith(".txt")) return lireCsv(contenu);
            if (nom.endsWith(".xls")) {
                throw ApiException.badRequest("Ancien format .xls non pris en charge : enregistrez le fichier en .xlsx ou .csv.");
            }
            throw ApiException.badRequest("Format non supporté. Utilisez un fichier .xlsx ou .csv.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Fichier illisible : vérifiez qu'il s'agit d'un .xlsx ou .csv valide.");
        }
    }

    // ── Excel ──────────────────────────────────────────────────────────

    private static List<Ligne> lireExcel(InputStream in) throws IOException {
        List<Ligne> lignes = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);

            int premiere = sheet.getFirstRowNum();
            Row entete = sheet.getRow(premiere);
            if (entete == null) return lignes;

            List<String> colonnes = new ArrayList<>();
            for (int c = 0; c < entete.getLastCellNum(); c++) {
                colonnes.add(TextUtils.normaliserEntete(valeur(entete.getCell(c), evaluator, formatter)));
            }

            for (int r = premiere + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> valeurs = new LinkedHashMap<>();
                boolean vide = true;
                for (int c = 0; c < colonnes.size(); c++) {
                    String v = valeur(row.getCell(c), evaluator, formatter).trim();
                    if (!v.isEmpty()) vide = false;
                    if (!colonnes.get(c).isEmpty()) valeurs.put(colonnes.get(c), v);
                }
                if (vide) continue;
                lignes.add(new Ligne(r + 1, valeurs));
                if (lignes.size() > MAX_LIGNES) throw ApiException.badRequest("Fichier trop long (20 000 lignes maximum).");
            }
        }
        return lignes;
    }

    private static String valeur(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA
                ? evaluator.evaluateFormulaCell(cell) : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) yield formatter.formatCellValue(cell, evaluator);
                double d = cell.getNumericCellValue();
                yield d == Math.rint(d) && Math.abs(d) < 1e15
                        ? String.valueOf((long) d)
                        : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    // ── CSV ────────────────────────────────────────────────────────────

    private static List<Ligne> lireCsv(byte[] bytes) {
        String texte = decoder(bytes);
        if (texte.startsWith("﻿")) texte = texte.substring(1);
        String[] lignesBrutes = texte.split("\\r?\\n|\\r");

        List<Ligne> lignes = new ArrayList<>();
        int i = 0;
        while (i < lignesBrutes.length && lignesBrutes[i].isBlank()) i++;
        if (i >= lignesBrutes.length) return lignes;

        String enteteBrute = lignesBrutes[i];
        char sep = compter(enteteBrute, ';') >= compter(enteteBrute, ',') ? ';' : ',';
        if (compter(enteteBrute, '\t') > compter(enteteBrute, sep)) sep = '\t';

        List<String> colonnes = new ArrayList<>();
        for (String h : decouper(enteteBrute, sep)) colonnes.add(TextUtils.normaliserEntete(h));

        for (int r = i + 1; r < lignesBrutes.length; r++) {
            if (lignesBrutes[r].isBlank()) continue;
            List<String> champs = decouper(lignesBrutes[r], sep);
            Map<String, String> valeurs = new LinkedHashMap<>();
            for (int c = 0; c < colonnes.size(); c++) {
                if (!colonnes.get(c).isEmpty()) {
                    valeurs.put(colonnes.get(c), c < champs.size() ? champs.get(c).trim() : "");
                }
            }
            lignes.add(new Ligne(r + 1, valeurs));
            if (lignes.size() > MAX_LIGNES) throw ApiException.badRequest("Fichier trop long (20 000 lignes maximum).");
        }
        return lignes;
    }

    /** UTF-8 si le contenu est valide, sinon Windows-1252 (CSV enregistré par Excel en français). */
    private static String decoder(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, WINDOWS_1252);
        }
    }

    private static int compter(String s, char c) {
        int n = 0;
        for (char x : s.toCharArray()) if (x == c) n++;
        return n;
    }

    /** Découpe une ligne CSV en respectant les guillemets ("a;b" reste un seul champ). */
    private static List<String> decouper(String ligne, char sep) {
        List<String> champs = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean guillemets = false;
        for (int k = 0; k < ligne.length(); k++) {
            char ch = ligne.charAt(k);
            if (ch == '"') {
                if (guillemets && k + 1 < ligne.length() && ligne.charAt(k + 1) == '"') {
                    cur.append('"');
                    k++;
                } else {
                    guillemets = !guillemets;
                }
            } else if (ch == sep && !guillemets) {
                champs.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        champs.add(cur.toString());
        return champs;
    }
}
