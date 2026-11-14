package com.chatbot.backend;

import com.chatbot.backend.config.AdminBootstrap;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.ParametreSystemeRepository;
import com.chatbot.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AdminBootstrapTest {

    private static final String MDP_TESTS = "Admin-Test-123"; // celui du application.yml de test

    @Autowired UserRepository users;
    @Autowired ParametreSystemeRepository parametres;
    @Autowired PasswordEncoder encoder;

    private void demarrerAvec(String motDePasse) {
        new AdminBootstrap(users, parametres, encoder, "admin", "admin@fpst.tn", motDePasse).run();
    }

    private User admin() {
        return users.findByUsername("admin").orElseThrow();
    }

    @Test
    void modifierAdminPasswordReinitialiseLeCompteUneSeuleFois() {
        try {
            demarrerAvec(MDP_TESTS);
            String hashInitial = admin().getPassword();
            assertTrue(encoder.matches(MDP_TESTS, hashInitial));

            // ADMIN_PASSWORD inchangé : le compte n'est pas touché
            demarrerAvec(MDP_TESTS);
            assertEquals(hashInitial, admin().getPassword());

            // ADMIN_PASSWORD modifié : nouveau mot de passe appliqué
            demarrerAvec("Nouveau2026");
            String hashNouveau = admin().getPassword();
            assertTrue(encoder.matches("Nouveau2026", hashNouveau));

            // Redémarrage sans changement : pas de nouvelle écriture
            demarrerAvec("Nouveau2026");
            assertEquals(hashNouveau, admin().getPassword());

            // Trop court : refusé, l'ancien reste valable
            demarrerAvec("court");
            assertTrue(encoder.matches("Nouveau2026", admin().getPassword()));

            // Vide : rien ne change
            demarrerAvec("");
            assertTrue(encoder.matches("Nouveau2026", admin().getPassword()));
        } finally {
            demarrerAvec(MDP_TESTS);
        }
        assertTrue(encoder.matches(MDP_TESTS, admin().getPassword()));
    }
}
