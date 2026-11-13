package com.chatbot.backend.registre;

import com.chatbot.backend.util.TextUtils;

import java.util.Optional;

/** Catégorie d'une personne dans le registre de l'établissement. */
public enum TypePersonne {
    ETUDIANT, PROFESSEUR;

    /** "Étudiant", "etudiant", "USER", "student" -> ETUDIANT ; "Professeur", "enseignant", "prof" -> PROFESSEUR. */
    public static Optional<TypePersonne> depuis(String valeur) {
        String v = TextUtils.normaliserEntete(valeur);
        if (v.isEmpty()) return Optional.empty();
        if (v.startsWith("etud") || v.equals("user") || v.startsWith("student") || v.equals("e")) {
            return Optional.of(ETUDIANT);
        }
        if (v.startsWith("prof") || v.startsWith("enseign") || v.startsWith("teacher") || v.equals("p")) {
            return Optional.of(PROFESSEUR);
        }
        return Optional.empty();
    }
}
