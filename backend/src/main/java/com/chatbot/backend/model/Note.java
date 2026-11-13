package com.chatbot.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Etudiant
    @ManyToOne
    @JoinColumn(name = "etudiant_id", nullable = false)
    private User etudiant;

    // Matière concernée (optionnelle si identifiée par nom)
    @ManyToOne
    @JoinColumn(name = "matiere_id", nullable = true)
    private Matiere matiere;

    // Nom de la matière (utile si la matière n'est pas en base)
    @Column(nullable = true)
    private String nomMatiere;

    // Type d'évaluation (DS, Examen, TP, Projet, Moyenne, etc.)
    @Column(nullable = false)
    private String typeEvaluation;

    // Note sur 20
    @Column(nullable = false)
    private Double valeur;

    // Moyenne associée (optionnelle)
    @Column(nullable = true)
    private Double moyenne;

    // Professeur qui a saisi la note (optionnel — null pour les imports admin)
    @ManyToOne
    @JoinColumn(name = "professeur_id", nullable = true)
    private User professeur;

    // Commentaire éventuel
    private String commentaire;

    // Source : "IMPORT" ou "MANUEL"
    @Column(nullable = false)
    private String source = "MANUEL";

    @Column(updatable = false)
    private LocalDateTime dateSaisie;

    @PrePersist
    protected void onCreate() {
        this.dateSaisie = LocalDateTime.now();
    }
}
