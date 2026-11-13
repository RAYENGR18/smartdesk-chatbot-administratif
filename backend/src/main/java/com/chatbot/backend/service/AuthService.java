package com.chatbot.backend.service;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.*;
import com.chatbot.backend.registre.PersonneEtablissement;
import com.chatbot.backend.registre.RegistreSource;
import com.chatbot.backend.registre.TypePersonne;
import com.chatbot.backend.repository.FiliereRepository;
import com.chatbot.backend.util.Niveaux;
import com.chatbot.backend.util.TextUtils;
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
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final RegistreSource registreSource;
    private final FiliereRepository filiereRepository;

    private static final String INSCRIPTION = "#inscription";
    private static final int MAX_ECHECS_INSCRIPTION = 10;

    // ============================================
    // INSCRIPTION VÉRIFIÉE PAR LE CIN
    // ============================================
    /**
     * L'utilisateur saisit nom, prénom, CIN (+ filière et niveau pour un étudiant).
     * Ces informations sont comparées à la base de l'établissement (RegistreSource) :
     * l'inscription n'est acceptée que si le CIN existe et que tout correspond.
     * Les nom, prénom, filière et niveau enregistrés sont ceux de l'établissement.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        if (loginAttemptService.isBlocked(INSCRIPTION, clientIp)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives d'inscription refusées. Réessayez dans "
                            + loginAttemptService.lockMinutes() + " minutes.");
        }

        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String cin = TextUtils.normaliserCin(request.getCin());

        String requestedRole = request.getRole() == null || request.getRole().isBlank()
                ? "USER" : request.getRole().trim().toUpperCase(Locale.ROOT);
        if (!requestedRole.equals("USER") && !requestedRole.equals("PROFESSEUR")) {
            throw ApiException.badRequest("Profil invalide : choisissez Étudiant ou Professeur.");
        }
        boolean etudiant = requestedRole.equals("USER");

        if (!cin.matches("[A-Z0-9]{5,20}")) {
            throw ApiException.badRequest("Numéro de CIN invalide.");
        }
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("Nom d'utilisateur déjà utilisé.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email déjà utilisé.");
        }

        // Filière et niveau choisis (étudiant)
        Filiere filiereChoisie = null;
        String niveauChoisi = null;
        if (etudiant) {
            if (request.getFiliereId() == null) {
                throw ApiException.badRequest("Veuillez choisir votre filière.");
            }
            filiereChoisie = filiereRepository.findById(request.getFiliereId())
                    .orElseThrow(() -> ApiException.badRequest("Filière inconnue."));
            niveauChoisi = Niveaux.normaliser(request.getNiveau())
                    .orElseThrow(() -> ApiException.badRequest("Veuillez choisir votre niveau d'études."));
        }

        // ── Vérification auprès de la base de l'établissement ──
        PersonneEtablissement personne = registreSource.trouverParCin(cin).orElse(null);
        if (personne == null) {
            loginAttemptService.failed(INSCRIPTION, clientIp, MAX_ECHECS_INSCRIPTION);
            throw ApiException.badRequest("Ce numéro de CIN ne figure pas dans la base de l'établissement. "
                    + "Vérifiez votre saisie ou contactez le service de scolarité.");
        }
        TypePersonne typeAttendu = etudiant ? TypePersonne.ETUDIANT : TypePersonne.PROFESSEUR;
        boolean correspond = personne.type() == typeAttendu
                && TextUtils.normaliserNom(personne.nom()).equals(TextUtils.normaliserNom(request.getLastName()))
                && TextUtils.normaliserNom(personne.prenom()).equals(TextUtils.normaliserNom(request.getFirstName()));
        if (correspond && etudiant) {
            correspond = filiereCorrespond(personne.filiere(), filiereChoisie)
                    && (personne.niveau() == null || personne.niveau().isBlank()
                        || Niveaux.normaliser(personne.niveau()).map(niveauChoisi::equals).orElse(false));
        }
        if (!correspond) {
            loginAttemptService.failed(INSCRIPTION, clientIp, MAX_ECHECS_INSCRIPTION);
            throw ApiException.badRequest(etudiant
                    ? "Les informations saisies (profil, nom, prénom, filière ou niveau) ne correspondent pas "
                      + "à celles enregistrées par l'établissement pour ce CIN."
                    : "Les informations saisies (profil, nom ou prénom) ne correspondent pas "
                      + "à celles enregistrées par l'établissement pour ce CIN.");
        }
        if (userRepository.findByCinIgnoreCase(cin).isPresent()) {
            throw ApiException.conflict("Un compte existe déjà pour ce CIN. Connectez-vous avec votre nom "
                    + "d'utilisateur ou contactez l'administration.");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.valueOf(requestedRole));

        // Identité officielle (orthographe de l'établissement)
        user.setFirstName(personne.prenom());
        user.setLastName(personne.nom());
        user.setCin(cin);
        user.setBirthDate(request.getBirthDate());
        user.setBirthPlace(request.getBirthPlace());
        user.setGender(request.getGender());
        user.setNationality(request.getNationality());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());

        if (etudiant) {
            user.setFiliere(filiereChoisie);
            user.setNiveau(niveauChoisi);
            user.setFieldOfStudy(filiereChoisie.getNom());
            user.setAcademicLevel(Niveaux.libelle(niveauChoisi));
            String matricule = personne.matricule();
            if (matricule != null && !matricule.isBlank() && userRepository.findByStudentId(matricule).isEmpty()) {
                user.setStudentId(matricule);
            }
        } else {
            user.setDepartment(personne.departement() != null && !personne.departement().isBlank()
                    ? personne.departement() : request.getDepartment());
        }
        user.setAcademicYear(request.getAcademicYear());
        user.setInstitution(request.getInstitution());
        user.setFaculty(request.getFaculty());
        user.setBacType(request.getBacType());
        user.setScholarship(request.getScholarship());
        user.setFatherName(request.getFatherName());
        user.setMotherName(request.getMotherName());
        user.setAdditionalInfo(request.getAdditionalInfo());

        User savedUser = userRepository.save(user);
        loginAttemptService.loginSucceeded(INSCRIPTION, clientIp);
        return toResponse(savedUser);
    }

    /** La filière du registre peut être un code ("INFO") ou un nom ("Informatique"). */
    private static boolean filiereCorrespond(String filiereRegistre, Filiere choisie) {
        if (filiereRegistre == null || filiereRegistre.isBlank()) return true;
        String r = TextUtils.normaliserNom(filiereRegistre);
        return r.equals(TextUtils.normaliserNom(choisie.getCode()))
                || r.equals(TextUtils.normaliserNom(choisie.getNom()));
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
