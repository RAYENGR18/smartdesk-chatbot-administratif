package com.chatbot.backend.registre;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registre local de l'établissement : liste officielle des étudiants et
 * professeurs, importée par l'administration (Excel/CSV) ou chargée en démo.
 */
@Entity
@Table(name = "registre_etablissement", indexes = @Index(name = "idx_registre_type", columnList = "type"))
@Data
@NoArgsConstructor
public class RegistreEtablissement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String cin;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypePersonne type;

    /** Code de la filière (étudiants). */
    @Column(length = 100)
    private String filiere;

    /** Niveau d'études : L1, L2, L3, M1, M2, ING1... (étudiants). */
    @Column(length = 10)
    private String niveau;

    @Column(length = 150)
    private String email;

    @Column(length = 50)
    private String matricule;

    @Column(length = 150)
    private String departement;

    /** IMPORT ou DEMO. */
    @Column(nullable = false, length = 10)
    private String origine = "IMPORT";

    private LocalDateTime dateMiseAJour = LocalDateTime.now();

    public PersonneEtablissement versPersonne() {
        return new PersonneEtablissement(cin, nom, prenom, type, filiere, niveau, email, matricule, departement);
    }
}
