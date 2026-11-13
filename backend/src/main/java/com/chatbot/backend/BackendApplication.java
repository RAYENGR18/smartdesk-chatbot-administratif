package com.chatbot.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Point d'entrée du backend SmartDesk.
 * Le compte administrateur initial est créé par config.AdminBootstrap.
 */
// Pas d'utilisateur "user" en mémoire généré par Spring : l'authentification passe par JWT
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
