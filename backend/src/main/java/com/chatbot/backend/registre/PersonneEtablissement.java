package com.chatbot.backend.registre;

/**
 * Une personne connue de l'établissement, quelle que soit la source
 * (registre importé localement ou base de données externe de la faculté).
 *
 * @param filiere code (ou nom) de la filière, pour un étudiant
 * @param niveau  niveau d'études (L1, M2...), pour un étudiant
 */
public record PersonneEtablissement(
        String cin,
        String nom,
        String prenom,
        TypePersonne type,
        String filiere,
        String niveau,
        String email,
        String matricule,
        String departement
) {
}
