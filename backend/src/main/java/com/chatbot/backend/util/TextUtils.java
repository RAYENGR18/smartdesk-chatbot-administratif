package com.chatbot.backend.util;

import java.text.Normalizer;
import java.util.Locale;

/** Normalisations utilisées pour comparer des noms, des CIN et des en-têtes de fichiers. */
public final class TextUtils {

    private TextUtils() {
    }

    /** Minuscules, sans accents, sans ponctuation, espaces simples : "Ben-Ali  Sâmi" -> "ben ali sami". */
    public static String normaliserNom(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u0600-\\u06FF]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** En-tête de colonne : "Nom de la Matière" -> "nomdelamatiere". */
    public static String normaliserEntete(String s) {
        return normaliserNom(s).replace(" ", "");
    }

    /**
     * CIN : sans espaces, en majuscules. Un CIN tunisien purement numérique de
     * moins de 8 chiffres (zéro initial perdu par Excel) est complété à 8 chiffres.
     */
    public static String normaliserCin(String s) {
        if (s == null) return "";
        String v = s.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
        if (v.matches("\\d+\\.0+")) {
            v = v.substring(0, v.indexOf('.'));   // 1234567.0 (cellule Excel numérique)
        }
        v = v.replace(".", "");
        if (v.matches("\\d{1,7}")) {
            v = "0".repeat(8 - v.length()) + v;   // zéro initial perdu par Excel
        }
        return v;
    }
}
