package com.chatbot.backend.security;

import com.chatbot.backend.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

/**
 * Accès à l'utilisateur connecté et contrôles d'accès "soi-même ou admin".
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw ApiException.unauthorized("Authentification requise.");
        }
        return user;
    }

    /** Autorise l'accès si la ressource appartient à l'utilisateur connecté ou s'il est admin. */
    public static AuthenticatedUser requireSelfOrAdmin(String username) {
        AuthenticatedUser me = get();
        if (!me.isAdmin() && !me.username().equals(username)) {
            throw ApiException.forbidden("Accès refusé.");
        }
        return me;
    }

    public static AuthenticatedUser requireSelfOrAdmin(Long userId) {
        AuthenticatedUser me = get();
        if (!me.isAdmin() && !Objects.equals(me.id(), userId)) {
            throw ApiException.forbidden("Accès refusé.");
        }
        return me;
    }
}
