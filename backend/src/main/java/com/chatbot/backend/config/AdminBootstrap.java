package com.chatbot.backend.config;

import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Crée le compte administrateur au premier démarrage, s'il n'existe
 * encore aucun admin. Le mot de passe vient de ADMIN_PASSWORD ; s'il est
 * vide, un mot de passe aléatoire est généré et affiché une seule fois.
 * Un compte existant n'est JAMAIS modifié (l'ancien code remettait
 * "admin123" à chaque redémarrage).
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.username:admin}") String username,
                          @Value("${app.admin.email:admin@fpst.tn}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.findByRole(User.Role.ADMIN).isEmpty()) {
            return;
        }
        if (userRepository.existsByUsername(username)) {
            log.warn("Aucun administrateur trouvé, mais le nom '{}' est déjà pris par un autre compte. "
                    + "Changez ADMIN_USERNAME dans le fichier .env.", username);
            return;
        }

        boolean generated = password == null || password.isBlank();
        String rawPassword = generated ? randomPassword() : password;
        if (!generated && rawPassword.length() < 8) {
            throw new IllegalStateException("ADMIN_PASSWORD doit contenir au moins 8 caractères.");
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
                     (Pour choisir le mot de passe, définissez ADMIN_PASSWORD
                      dans .env AVANT le premier démarrage.)
                    ==============================================================""",
                    username, rawPassword);
        } else {
            log.info("Compte administrateur '{}' créé avec le mot de passe défini dans ADMIN_PASSWORD.", username);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
