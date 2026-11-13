package com.chatbot.backend.security;

import com.chatbot.backend.model.User;

/**
 * Utilisateur connecté, tel que reconstruit à partir du token JWT
 * et de la base (le rôle vient toujours de la base, pas du token).
 */
public record AuthenticatedUser(Long id, String username, User.Role role) {

    public boolean isAdmin() {
        return role == User.Role.ADMIN;
    }

    public boolean isProfesseur() {
        return role == User.Role.PROFESSEUR;
    }

    public boolean isEtudiant() {
        return role == User.Role.USER;
    }
}
