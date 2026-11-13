package com.chatbot.backend.controller;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.UserRepository;
import com.chatbot.backend.security.AuthenticatedUser;
import com.chatbot.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private static final int MAX_FIELD_LENGTH = 255;

    private final UserRepository userRepository;

    // PROFIL DE L'UTILISATEUR CONNECTÉ
    @GetMapping({"/profile", "/me"})
    public ResponseEntity<Map<String, Object>> getMyProfile() {
        AuthenticatedUser me = CurrentUser.get();
        return ResponseEntity.ok(toProfile(findByUsername(me.username())));
    }

    // PROFIL PAR NOM D'UTILISATEUR (soi-même ou admin)
    @GetMapping("/profile/{username}")
    public ResponseEntity<Map<String, Object>> getProfileByUsername(@PathVariable String username) {
        CurrentUser.requireSelfOrAdmin(username);
        return ResponseEntity.ok(toProfile(findByUsername(username)));
    }

    // METTRE À JOUR LE PROFIL (soi-même ou admin)
    @PutMapping("/profile/{username}")
    public ResponseEntity<Map<String, String>> updateProfile(@PathVariable String username,
                                                             @RequestBody Map<String, String> updates) {
        CurrentUser.requireSelfOrAdmin(username);
        User user = findByUsername(username);

        for (Map.Entry<String, String> e : updates.entrySet()) {
            if (e.getValue() != null && e.getValue().length() > MAX_FIELD_LENGTH) {
                throw ApiException.badRequest("Le champ '" + e.getKey() + "' est trop long.");
            }
        }

        // Nom et prénom viennent de l'identité administrative : non modifiables ici
        if (updates.containsKey("phone")) user.setPhone(updates.get("phone"));
        if (updates.containsKey("address")) user.setAddress(updates.get("address"));
        if (updates.containsKey("fieldOfStudy")) user.setFieldOfStudy(updates.get("fieldOfStudy"));
        if (updates.containsKey("academicLevel")) user.setAcademicLevel(updates.get("academicLevel"));
        if (updates.containsKey("email")) {
            String email = updates.get("email") == null ? "" : updates.get("email").trim().toLowerCase(Locale.ROOT);
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw ApiException.badRequest("Email invalide.");
            }
            if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
                throw ApiException.conflict("Email déjà utilisé.");
            }
            user.setEmail(email);
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Profil mis à jour avec succès"));
    }

    // FILIÈRES DISTINCTES DES ÉTUDIANTS (admin : ciblage des notifications)
    @GetMapping("/filieres")
    public ResponseEntity<List<String>> getFilieres() {
        List<String> filieres = userRepository.findByRole(User.Role.USER).stream()
                .map(User::getFieldOfStudy)
                .filter(f -> f != null && !f.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(filieres);
    }

    // LISTE DES PROFESSEURS (admin : ciblage des notifications)
    @GetMapping("/professors")
    public ResponseEntity<List<Map<String, Object>>> getProfessors() {
        List<Map<String, Object>> profs = userRepository.findByRole(User.Role.PROFESSEUR).stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("firstName", Objects.toString(u.getFirstName(), ""));
                    m.put("lastName", Objects.toString(u.getLastName(), ""));
                    m.put("email", Objects.toString(u.getEmail(), ""));
                    m.put("department", Objects.toString(u.getDepartment(), ""));
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(profs);
    }

    // NOM D'UN ÉTUDIANT PAR ID (admin : ciblage)
    @GetMapping("/{id}/name")
    public ResponseEntity<Map<String, Object>> getStudentById(@PathVariable Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == User.Role.USER)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("firstName", Objects.toString(u.getFirstName(), ""));
                    m.put("lastName", Objects.toString(u.getLastName(), ""));
                    m.put("fieldOfStudy", Objects.toString(u.getFieldOfStudy(), ""));
                    return ResponseEntity.ok(m);
                })
                .orElseThrow(() -> ApiException.notFound("Étudiant non trouvé."));
    }

    private User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("Utilisateur non trouvé."));
    }

    private static Map<String, Object> toProfile(User user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("firstName", user.getFirstName());
        profile.put("lastName", user.getLastName());
        profile.put("cin", user.getCin());
        profile.put("phone", user.getPhone());
        profile.put("address", user.getAddress());
        profile.put("gender", user.getGender());
        profile.put("nationality", user.getNationality());
        profile.put("birthDate", user.getBirthDate());
        profile.put("birthPlace", user.getBirthPlace());
        profile.put("studentId", user.getStudentId());
        profile.put("fieldOfStudy", user.getFieldOfStudy());
        profile.put("academicLevel", user.getAcademicLevel());
        profile.put("academicYear", user.getAcademicYear());
        profile.put("institution", user.getInstitution());
        profile.put("faculty", user.getFaculty());
        profile.put("department", user.getDepartment());
        profile.put("additionalInfo", user.getAdditionalInfo());
        profile.put("role", user.getRole() != null ? user.getRole().name() : "USER");
        profile.put("specialite", user.getSpecialite());
        profile.put("filieresProfesseur", user.getFilieresProfesseur());
        profile.put("matieresEnseignees", user.getMatieresEnseignees());
        return profile;
    }
}
