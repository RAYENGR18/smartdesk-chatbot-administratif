package com.chatbot.backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    // ===== PROFIL ÉTENDU =====
    private String firstName;
    private String lastName;
    private String cin;
    private String phone;
    private String address;
    private String gender;
    private String nationality;
    private String birthDate;
    private String birthPlace;

    // ===== INFOS ACADÉMIQUES =====
    @Column(unique = true)
    private String studentId;
    private String fieldOfStudy;
    private String academicLevel;
    private String academicYear;
    private String institution;
    private String faculty;
    private String department;

    // ===== SPÉCIALITÉ ET FILIÈRES (pour les professeurs) =====
    private String specialite;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "professeur_filieres",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "filiere_id")
    )
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private java.util.Set<Filiere> filieresProfesseur = new java.util.HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "professeur_matieres",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "matiere_id")
    )
    @JsonIgnoreProperties({"filieres", "filiere"})
    private java.util.Set<com.chatbot.backend.model.Matiere> matieresEnseignees = new java.util.HashSet<>();

    // ===== INFOS SUPPLÉMENTAIRES =====
    private String fatherName;
    private String motherName;
    private String bacType;
    private String scholarship;

    @Column(length = 2000)
    private String additionalInfo;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // 3 types de rôles : étudiant (USER), professeur, administrateur
    public enum Role {
        USER, ADMIN, PROFESSEUR
    }
}
