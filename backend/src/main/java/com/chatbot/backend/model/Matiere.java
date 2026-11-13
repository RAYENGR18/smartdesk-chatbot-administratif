package com.chatbot.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "matieres")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Matiere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(unique = true, nullable = false)
    private String code;

    // Coefficient de la matiere
    private Double coefficient;

    // Volume horaire par semaine (en heures)
    private Integer volumeHoraire;

    // Type : CM (Cours Magistral), TD (Travaux Diriges), TP (Travaux Pratiques)
    @Enumerated(EnumType.STRING)
    private TypeMatiere type = TypeMatiere.CM;

    // Niveau d'études (L1, L2, M1...) ; vide = matière commune à tous les niveaux
    @Column(length = 10)
    private String niveau;

    // Many-to-Many: une matiere peut appartenir a plusieurs filieres
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "matiere_filiere",
        joinColumns = @JoinColumn(name = "matiere_id"),
        inverseJoinColumns = @JoinColumn(name = "filiere_id")
    )
    @JsonIgnoreProperties({"matieres"})
    private Set<Filiere> filieres = new HashSet<>();

    // Keep backward compatibility: single filiere for legacy code
    @ManyToOne
    @JoinColumn(name = "filiere_id")
    @JsonIgnoreProperties({"matieres"})
    private Filiere filiere;

    public enum TypeMatiere {
        CM, TD, TP
    }
}
