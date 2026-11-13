package com.chatbot.backend.registre;

import java.util.Optional;

/**
 * Source de vérité utilisée pour vérifier l'identité lors de l'inscription.
 *
 * Deux implémentations, choisies par REGISTRE_MODE dans .env :
 *  - "local"   : registre importé (Excel/CSV) ou données de démonstration ;
 *  - "externe" : base de données de la faculté, en lecture seule.
 * Ajouter une autre source (API, LDAP...) = écrire une nouvelle implémentation.
 */
public interface RegistreSource {

    /** Recherche une personne par son CIN déjà normalisé (voir TextUtils.normaliserCin). */
    Optional<PersonneEtablissement> trouverParCin(String cin);

    /** "local" ou "externe" (affiché dans l'interface admin). */
    String mode();
}
