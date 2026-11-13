package com.chatbot.backend.controller;

import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:61748"})
@RequiredArgsConstructor
public class StudentController {

    private final UserRepository userRepository;


    // RÉCUPÉRER LE PROFIL ÉTUDIANT

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader(value = "Authorization", required = false) String authHeader) {

        return ResponseEntity.ok(Map.of("message", "Utilisez /profile/{username}"));
    }

    @GetMapping("/profile/{username}")
    public ResponseEntity<?> getProfileByUsername(@PathVariable String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

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
        profile.put("role", user.getRole().name());
        profile.put("specialite", user.getSpecialite());
        profile.put("filieresProfesseur", user.getFilieresProfesseur());
        profile.put("matieresEnseignees", user.getMatieresEnseignees());

        return ResponseEntity.ok(profile);
    }

    // ============================================
    // METTRE À JOUR LE PROFIL
    // ============================================
    @PutMapping("/profile/{username}")
    public ResponseEntity<?> updateProfile(
            @PathVariable String username,
            @RequestBody Map<String, String> updates
    ) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (updates.containsKey("firstName")) user.setFirstName(updates.get("firstName"));
        if (updates.containsKey("lastName")) user.setLastName(updates.get("lastName"));
        if (updates.containsKey("phone")) user.setPhone(updates.get("phone"));
        if (updates.containsKey("address")) user.setAddress(updates.get("address"));
        if (updates.containsKey("email")) user.setEmail(updates.get("email"));
        if (updates.containsKey("fieldOfStudy")) user.setFieldOfStudy(updates.get("fieldOfStudy"));
        if (updates.containsKey("academicLevel")) user.setAcademicLevel(updates.get("academicLevel"));

        userRepository.save(user);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Profil mis à jour avec succès");
        return ResponseEntity.ok(response);
    }

    // ============================================
    // FILIÈRES DISTINCTES (pour admin notifications)
    // ============================================
    @GetMapping("/filieres")
    public ResponseEntity<List<String>> getFilieres() {
        List<String> filieres = userRepository.findByRole(User.Role.USER)
                .stream()
                .map(User::getFieldOfStudy)
                .filter(f -> f != null && !f.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(filieres);
    }

    // ============================================
    // LISTE DES PROFESSEURS (pour admin notifications)
    // ============================================
    @GetMapping("/professors")
    public ResponseEntity<List<Map<String, Object>>> getProfessors() {
        List<Map<String, Object>> profs = userRepository.findByRole(User.Role.PROFESSEUR)
                .stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("firstName", u.getFirstName() != null ? u.getFirstName() : "");
                    m.put("lastName", u.getLastName() != null ? u.getLastName() : "");
                    m.put("email", u.getEmail() != null ? u.getEmail() : "");
                    m.put("department", u.getDepartment() != null ? u.getDepartment() : "");
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(profs);
    }

    // ============================================
    // CHERCHER UN ÉTUDIANT PAR ID (pour ciblage)
    // ============================================
    @GetMapping("/{id}/name")
    public ResponseEntity<?> getStudentById(@PathVariable Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == User.Role.USER)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("firstName", u.getFirstName() != null ? u.getFirstName() : "");
                    m.put("lastName", u.getLastName() != null ? u.getLastName() : "");
                    m.put("fieldOfStudy", u.getFieldOfStudy() != null ? u.getFieldOfStudy() : "");
                    return ResponseEntity.ok(m);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
