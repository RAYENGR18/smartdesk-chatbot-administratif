package com.chatbot.backend.service;

import com.chatbot.backend.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
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

    public DatabaseResetService(UserRepository userRepository,
                               NoteRepository noteRepository,
                               MatiereRepository matiereRepository,
                               ChatMessageRepository chatMessageRepository,
                               DocumentRepository documentRepository,
                               EmploiDuTempsRepository emploiDuTempsRepository,
                               FiliereRepository filiereRepository,
                               SalleRepository salleRepository,
                               NotificationRepository notificationRepository,
                               UniversityIdentityRepository universityIdentityRepository) {
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.matiereRepository = matiereRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
        this.emploiDuTempsRepository = emploiDuTempsRepository;
        this.filiereRepository = filiereRepository;
        this.salleRepository = salleRepository;
        this.notificationRepository = notificationRepository;
        this.universityIdentityRepository = universityIdentityRepository;
    }

    @Transactional
    public void resetAll() {
        // Delete dependent entities first to satisfy foreign‑key constraints
        noteRepository.deleteAllInBatch();
        chatMessageRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        emploiDuTempsRepository.deleteAllInBatch();
        filiereRepository.deleteAllInBatch();
        matiereRepository.deleteAllInBatch();
        salleRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        universityIdentityRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
