package com.chatbot.backend.service;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.*;
import com.chatbot.backend.repository.UniversityIdentityRepository;
import com.chatbot.backend.repository.UserRepository;
import com.chatbot.backend.security.JwtService;
import com.chatbot.backend.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Hash BCrypt factice : évite de révéler par le temps de réponse qu'un compte n'existe pas. */
    private volatile String dummyHash;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UniversityIdentityRepository universityIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    // ============================================
    // INSCRIPTION AVEC PROFIL COMPLET
    // ============================================
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("Nom d'utilisateur déjà utilisé.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email déjà utilisé.");
        }

        // Seuls les rôles étudiant (USER) et professeur peuvent s'inscrire
        String requestedRole = request.getRole() == null || request.getRole().isBlank()
                ? "USER" : request.getRole().trim().toUpperCase(Locale.ROOT);
        if (!requestedRole.equals("USER") && !requestedRole.equals("PROFESSEUR")) {
            throw ApiException.badRequest("Rôle invalide.");
        }

        // L'identifiant doit avoir été généré par l'administration
        String providedId = request.getStudentId().trim();
        UniversityIdentity identity = universityIdentityRepository.findByIdentifier(providedId)
                .orElseThrow(() -> ApiException.badRequest("Identifiant ou CIN incorrect."));

        if (identity.getCin() == null
                || !identity.getCin().trim().equalsIgnoreCase(request.getCin().trim())) {
            throw ApiException.badRequest("Identifiant ou CIN incorrect.");
        }
        if (identity.isUsed()) {
            throw ApiException.conflict("Cet identifiant a déjà été utilisé pour une inscription.");
        }
        if (!identity.getRole().equals(requestedRole)) {
            throw ApiException.badRequest("Le rôle choisi ne correspond pas à l'identifiant fourni.");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.valueOf(requestedRole));

        // Nom, prénom et CIN viennent de l'identité créée par l'administration (intégrité)
        user.setFirstName(identity.getFirstName());
        user.setLastName(identity.getLastName());
        user.setCin(identity.getCin());
        user.setBirthDate(request.getBirthDate());
        user.setBirthPlace(request.getBirthPlace());
        user.setGender(request.getGender());
        user.setNationality(request.getNationality());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());

        // Profil académique
        user.setStudentId(providedId);
        user.setFieldOfStudy(request.getFieldOfStudy());
        user.setAcademicLevel(request.getAcademicLevel());
        user.setAcademicYear(request.getAcademicYear());
        user.setInstitution(request.getInstitution());
        user.setFaculty(request.getFaculty());
        user.setDepartment(request.getDepartment());
        user.setBacType(request.getBacType());
        user.setScholarship(request.getScholarship());

        // Famille
        user.setFatherName(request.getFatherName());
        user.setMotherName(request.getMotherName());
        user.setAdditionalInfo(request.getAdditionalInfo());

        User savedUser = userRepository.save(user);

        identity.setUsed(true);
        universityIdentityRepository.save(identity);

        return toResponse(savedUser);
    }

    // ============================================
    // CONNEXION
    // ============================================
    public AuthResponse login(LoginRequest request, String clientIp) {
        String username = request.getUsername().trim();

        if (loginAttemptService.isBlocked(username, clientIp)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives échouées. Réessayez dans "
                            + loginAttemptService.lockMinutes() + " minutes.");
        }

        User user = userRepository.findByUsername(username).orElse(null);
        String hash = user != null ? user.getPassword() : dummyHash();
        boolean passwordOk = passwordEncoder.matches(request.getPassword(), hash);

        if (user == null || !passwordOk) {
            loginAttemptService.loginFailed(username, clientIp);
            // Message volontairement identique : on ne révèle pas si le compte existe
            throw ApiException.unauthorized("Nom d'utilisateur ou mot de passe incorrect.");
        }

        loginAttemptService.loginSucceeded(username, clientIp);
        return toResponse(user);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
        }
        return dummyHash;
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getRole().name(), user.getId());
    }
}
