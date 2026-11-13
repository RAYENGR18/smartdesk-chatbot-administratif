package com.chatbot.backend.service;

import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * Remise à zéro des données (réservée à l'admin, avec confirmation).
 * Les comptes administrateurs sont conservés pour ne jamais se retrouver
 * sans accès à l'application.
 */
@Service
@RequiredArgsConstructor
public class DatabaseResetService {

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final MatiereRepository matiereRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final FiliereRepository filiereRepository;
    private final SalleRepository salleRepository;
    private final NotificationRepository notificationRepository;
    private final UniversityIdentityRepository universityIdentityRepository;
    private final DocumentService documentService;

    @Transactional
    public void resetAll() {
        documentRepository.findAll().forEach(documentService::deleteFileQuietly);

        // Ordre : d'abord les entités qui référencent les autres
        noteRepository.deleteAllInBatch();
        chatMessageRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        emploiDuTempsRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        universityIdentityRepository.deleteAllInBatch();

        // Comptes non-admin (les tables de liaison sont vidées via l'entité)
        List<User> nonAdmins = userRepository.findAll().stream()
                .filter(u -> u.getRole() != User.Role.ADMIN)
                .toList();
        userRepository.deleteAll(nonAdmins);
        userRepository.findByRole(User.Role.ADMIN).forEach(admin -> {
            admin.setFilieresProfesseur(new HashSet<>());
            admin.setMatieresEnseignees(new HashSet<>());
            userRepository.save(admin);
        });
        userRepository.flush();

        matiereRepository.deleteAll();
        filiereRepository.deleteAll();
        salleRepository.deleteAllInBatch();
    }
}
