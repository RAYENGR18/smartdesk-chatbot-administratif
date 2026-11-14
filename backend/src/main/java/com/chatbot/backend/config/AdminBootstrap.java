package com.chatbot.backend.config;

import com.chatbot.backend.model.ParametreSysteme;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.ParametreSystemeRepository;
import com.chatbot.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Compte administrateur.
 * <ul>
 *   <li>Premier démarrage : crée le compte avec ADMIN_PASSWORD (ou un mot de passe
 *       aléatoire affiché une seule fois si ADMIN_PASSWORD est vide).</li>
 *   <li>Démarrages suivants : si ADMIN_PASSWORD a été modifié dans .env, le mot de passe
 *       du compte admin est mis à jour (c'est la façon de le réinitialiser). Tant que
 *       ADMIN_PASSWORD ne change pas, le compte n'est jamais touché : on mémorise une
 *       empreinte BCrypt de la dernière valeur appliquée, jamais la valeur elle-même.</li>
 * </ul>
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final String CLE_EMPREINTE = "admin.mot-de-passe-env";
    private static final int LONGUEUR_MIN = 8;

    private final UserRepository userRepository;
    private final ParametreSystemeRepository parametres;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository,
                          ParametreSystemeRepository parametres,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.username:admin}") String username,
                          @Value("${app.admin.email:admin@fpst.tn}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.parametres = parametres;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByRole(User.Role.ADMIN).isEmpty()) {
            creerAdmin();
        } else {
            synchroniserMotDePasse();
        }
    }

    private void creerAdmin() {
        if (userRepository.existsByUsername(username)) {
            log.warn("Aucun administrateur trouvé, mais le nom '{}' est déjà pris par un autre compte. "
                    + "Changez ADMIN_USERNAME dans le fichier .env.", username);
            return;
        }

        boolean generated = password == null || password.isBlank();
        String rawPassword = generated ? randomPassword() : password;
        if (!generated && rawPassword.length() < LONGUEUR_MIN) {
            throw new IllegalStateException("ADMIN_PASSWORD doit contenir au moins " + LONGUEUR_MIN + " caractères.");
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setRole(User.Role.ADMIN);
        admin.setFirstName("Super");
        admin.setLastName("Admin");
        userRepository.save(admin);

        if (generated) {
            log.warn("""

                    ==============================================================
                     Compte administrateur créé
                       utilisateur  : {}
                       mot de passe : {}
                     Notez-le maintenant : il ne sera plus affiché.
                     (Pour choisir le mot de passe : ADMIN_PASSWORD dans .env,
                      puis redémarrez le backend.)
                    ==============================================================""",
                    username, rawPassword);
        } else {
            memoriserEmpreinte(rawPassword);
            log.info("Compte administrateur '{}' créé avec le mot de passe défini dans ADMIN_PASSWORD.", username);
        }
    }

    /** Applique ADMIN_PASSWORD au compte admin s'il a changé depuis la dernière application. */
    private void synchroniserMotDePasse() {
        if (password == null || password.isBlank()) {
            return;
        }
        Optional<User> compte = userRepository.findByUsername(username)
                .filter(u -> u.getRole() == User.Role.ADMIN);
        if (compte.isEmpty()) {
            log.warn("ADMIN_PASSWORD ignoré : aucun administrateur nommé '{}' (ADMIN_USERNAME).", username);
            return;
        }
        User admin = compte.get();

        Optional<ParametreSysteme> empreinte = parametres.findById(CLE_EMPREINTE);
        boolean dejaApplique = empreinte.isPresent()
                ? passwordEncoder.matches(password, empreinte.get().getValeur())
                : passwordEncoder.matches(password, admin.getPassword());
        if (dejaApplique) {
            if (empreinte.isEmpty()) {
                memoriserEmpreinte(password);
            }
            return;
        }

        if (password.length() < LONGUEUR_MIN) {
            log.error("ADMIN_PASSWORD a été modifié mais contient moins de {} caractères : "
                    + "le mot de passe administrateur n'a PAS été changé.", LONGUEUR_MIN);
            return;
        }
        admin.setPassword(passwordEncoder.encode(password));
        userRepository.save(admin);
        memoriserEmpreinte(password);
        log.warn("Mot de passe du compte administrateur '{}' mis à jour depuis ADMIN_PASSWORD (.env).", username);
    }

    private void memoriserEmpreinte(String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword);
        ParametreSysteme p = parametres.findById(CLE_EMPREINTE).orElseGet(() -> new ParametreSysteme(CLE_EMPREINTE, hash));
        p.setValeur(hash);
        parametres.save(p);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
