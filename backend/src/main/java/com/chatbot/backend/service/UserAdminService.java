package com.chatbot.backend.service;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.DocumentRequest;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.*;
import com.chatbot.backend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Opérations d'administration sur les comptes, avec garde-fous :
 * un admin ne peut pas se supprimer ou se rétrograder lui-même,
 * et le dernier administrateur ne peut pas disparaître.
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final NoteRepository noteRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final DocumentService documentService;
    private final com.chatbot.backend.presence.PresenceRepository presenceRepository;
    private final com.chatbot.backend.presence.ScanRfidRepository scanRfidRepository;

    @Transactional
    public void deleteUser(Long id, AuthenticatedUser me) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilisateur non trouvé."));

        if (Objects.equals(user.getId(), me.id())) {
            throw ApiException.badRequest("Vous ne pouvez pas supprimer votre propre compte.");
        }
        if (user.getRole() == User.Role.ADMIN && userRepository.countByRole(User.Role.ADMIN) <= 1) {
            throw ApiException.badRequest("Impossible de supprimer le dernier administrateur.");
        }

        // Suppression des données liées (sinon violation de clé étrangère)
        chatMessageRepository.deleteAllByUserId(user.getId());
        noteRepository.deleteAllByEtudiantId(user.getId());
        noteRepository.detachProfesseur(user.getId());
        emploiDuTempsRepository.detachProfesseur(user.getId());
        presenceRepository.deleteAllByProfesseurId(user.getId());
        scanRfidRepository.detachProfesseur(user.getId());

        List<DocumentRequest> docs = documentRepository.findByUserId(user.getId());
        docs.forEach(documentService::deleteFileQuietly);
        documentRepository.deleteAll(docs);

        user.setFilieresProfesseur(new HashSet<>());
        user.setMatieresEnseignees(new HashSet<>());
        userRepository.delete(user);
        log.info("Utilisateur {} supprimé par {}", user.getUsername(), me.username());
    }

    @Transactional
    public User changeRole(Long id, String newRole, AuthenticatedUser me) {
        if (newRole == null || newRole.isBlank()) {
            throw ApiException.badRequest("Rôle invalide. Utilisez : USER, ADMIN, PROFESSEUR.");
        }
        User.Role role;
        try {
            role = User.Role.valueOf(newRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Rôle invalide. Utilisez : USER, ADMIN, PROFESSEUR.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilisateur non trouvé."));

        if (Objects.equals(user.getId(), me.id()) && role != User.Role.ADMIN) {
            throw ApiException.badRequest("Vous ne pouvez pas retirer votre propre rôle d'administrateur.");
        }
        if (user.getRole() == User.Role.ADMIN && role != User.Role.ADMIN
                && userRepository.countByRole(User.Role.ADMIN) <= 1) {
            throw ApiException.badRequest("Impossible de rétrograder le dernier administrateur.");
        }

        user.setRole(role);
        log.info("Rôle de {} changé en {} par {}", user.getUsername(), role, me.username());
        return userRepository.save(user);
    }
}
