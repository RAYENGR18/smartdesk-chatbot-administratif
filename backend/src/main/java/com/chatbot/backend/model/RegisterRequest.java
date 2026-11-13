package com.chatbot.backend.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Données d'inscription (étudiant ou professeur). */
@Data
public class RegisterRequest {

    // Compte
    @NotBlank(message = "Le nom d'utilisateur est obligatoire.")
    @Size(min = 3, max = 50, message = "Le nom d'utilisateur doit contenir entre 3 et 50 caractères.")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
            message = "Le nom d'utilisateur ne peut contenir que des lettres, chiffres, '.', '_' et '-'.")
    private String username;

    @NotBlank(message = "L'email est obligatoire.")
    @Email(message = "Email invalide.")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire.")
    @Size(min = 8, max = 100, message = "Le mot de passe doit contenir au moins 8 caractères.")
    private String password;

    // Profil personnel
    @Size(max = 100) private String firstName;
    @Size(max = 100) private String lastName;
    @NotBlank(message = "Le CIN est obligatoire.")
    @Size(max = 30) private String cin;
    @Size(max = 30) private String birthDate;
    @Size(max = 100) private String birthPlace;
    @Size(max = 20) private String gender;
    @Size(max = 60) private String nationality;
    @Size(max = 30) private String phone;
    @Size(max = 255) private String address;

    // Profil académique
    @NotBlank(message = "L'identifiant administratif est obligatoire.")
    @Size(max = 30) private String studentId;
    @Size(max = 255) private String fieldOfStudy;
    @Size(max = 60) private String academicLevel;
    @Size(max = 20) private String academicYear;
    @Size(max = 255) private String institution;
    @Size(max = 150) private String faculty;
    @Size(max = 255) private String department;
    @Size(max = 60) private String bacType;
    @Size(max = 60) private String scholarship;

    // Famille
    @Size(max = 150) private String fatherName;
    @Size(max = 150) private String motherName;

    // Infos supplémentaires (JSON sérialisé)
    @Size(max = 2000) private String additionalInfo;

    // Rôle demandé : USER (étudiant) ou PROFESSEUR
    @Size(max = 20) private String role;
}
