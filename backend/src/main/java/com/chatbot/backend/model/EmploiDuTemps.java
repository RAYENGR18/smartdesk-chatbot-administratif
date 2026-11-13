package com.chatbot.backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalTime;

@Entity
@Table(name = "emploi_du_temps")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmploiDuTemps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Jour de la semaine
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Jour jour;

    // Heure de début du créneau
    @Column(nullable = false)
    private LocalTime heureDebut;

    // Heure de fin du créneau
    @Column(nullable = false)
    private LocalTime heureFin;

    // Matière enseignée dans ce créneau
    @ManyToOne
    @JoinColumn(name = "matiere_id", nullable = false)
    private Matiere matiere;

    // Salle assignée
    @ManyToOne
    @JoinColumn(name = "salle_id", nullable = false)
    private Salle salle;

    // Filière concernée
    @ManyToOne
    @JoinColumn(name = "filiere_id", nullable = false)
    private Filiere filiere;

    // Niveau d'études concerné (L1, L2, M1...) : un étudiant ne voit que
    // les créneaux de SA filière et de SON niveau
    @Column(length = 10)
    private String niveau;

    // Professeur assigné (seuls ses nom et prénom sont exposés dans l'emploi du temps)
    @ManyToOne
    @JoinColumn(name = "professeur_id")
    @JsonIgnoreProperties(value = {"email", "cin", "phone", "address", "gender", "nationality", "birthDate",
            "birthPlace", "studentId", "fieldOfStudy", "academicLevel", "academicYear", "institution",
            "faculty", "department", "filieresProfesseur", "matieresEnseignees", "fatherName", "motherName",
            "bacType", "scholarship", "additionalInfo", "createdAt", "filiere", "niveau", "role",
            "hibernateLazyInitializer", "handler"}, allowSetters = true)
    private User professeur;

    // Groupe (ex: Groupe A, Groupe B pour les TD)
    private String groupe;

    // Semestre (S1, S2)
    private String semestre;

    public enum Jour {
        LUNDI, MARDI, MERCREDI, JEUDI, VENDREDI, SAMEDI
    }
}
