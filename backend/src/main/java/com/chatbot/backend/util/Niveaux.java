package com.chatbot.backend.util;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Niveaux d'études reconnus par la plateforme (code court + libellé).
 * Accepte en entrée les formes usuelles : "L1", "Licence 1", "licence1", "1ère année licence"...
 */
public final class Niveaux {

    private static final Map<String, String> LIBELLES = new LinkedHashMap<>();

    static {
        LIBELLES.put("L1", "Licence 1");
        LIBELLES.put("L2", "Licence 2");
        LIBELLES.put("L3", "Licence 3");
        LIBELLES.put("M1", "Master 1");
        LIBELLES.put("M2", "Master 2");
        LIBELLES.put("ING1", "Ingénieur 1");
        LIBELLES.put("ING2", "Ingénieur 2");
        LIBELLES.put("ING3", "Ingénieur 3");
    }

    private Niveaux() {
    }

    public static Map<String, String> tous() {
        return LIBELLES;
    }

    public static String libelle(String code) {
        return code == null ? null : LIBELLES.getOrDefault(code, code);
    }

    /** Convertit une saisie libre en code (L1, M2, ING3...) ; vide si non reconnue. */
    public static Optional<String> normaliser(String valeur) {
        if (valeur == null || valeur.isBlank()) return Optional.empty();
        String v = Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (LIBELLES.containsKey(v)) return Optional.of(v);

        String chiffre = v.replaceAll("[^0-9]", "");
        if (chiffre.isEmpty()) return Optional.empty();
        String n = chiffre.substring(0, 1);
        String lettres = v.replaceAll("[0-9]", "");
        String prefixe;
        if (lettres.contains("ING")) prefixe = "ING";
        else if (lettres.startsWith("M") || lettres.contains("MAST")) prefixe = "M";
        else if (lettres.startsWith("L") || lettres.contains("LIC")) prefixe = "L";
        else return Optional.empty();

        String code = prefixe + n;
        return LIBELLES.containsKey(code) ? Optional.of(code) : Optional.empty();
    }
}
