package com.chatbot.backend.repository;
//on import la classe user
import com.chatbot.backend.model.User;
//JpaRepository C’est une interface fournie par Spring Data JPA il donne des methodes pour lire supp compter et tout dans ecrire un long code sql
import org.springframework.data.jpa.repository.JpaRepository;
//Ce fichier est un composant d'accès à la base de données
import org.springframework.stereotype.Repository;
//Optional Quand tu cherches un utilisateur :peut exister  ou non
import java.util.Optional;

@Repository
//UserRepository reçoit toutes les méthodes de JpaRepository.
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    java.util.List<User> findByRole(com.chatbot.backend.model.User.Role role);
    Optional<User> findByCinIgnoreCase(String cin);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByStudentId(String studentId);
    Optional<User> findByRfidUid(String rfidUid);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    long countByRole(com.chatbot.backend.model.User.Role role);

    /** Recherche des fiches (admin) : rôle, filière, niveau et texte libre (nom, prénom, CIN, identifiant, email, matricule). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT u FROM User u LEFT JOIN u.filiere f
            WHERE u.role = :role
              AND (:filiereId IS NULL OR f.id = :filiereId)
              AND (:niveau = '' OR u.niveau = :niveau)
              AND (:q = '' OR LOWER(COALESCE(u.lastName, '')) LIKE CONCAT('%', :q, '%')
                   OR LOWER(COALESCE(u.firstName, '')) LIKE CONCAT('%', :q, '%')
                   OR LOWER(COALESCE(u.cin, '')) LIKE CONCAT('%', :q, '%')
                   OR LOWER(u.username) LIKE CONCAT('%', :q, '%')
                   OR LOWER(u.email) LIKE CONCAT('%', :q, '%')
                   OR LOWER(COALESCE(u.studentId, '')) LIKE CONCAT('%', :q, '%'))
            """)
    org.springframework.data.domain.Page<User> rechercherFiches(
            @org.springframework.data.repository.query.Param("role") User.Role role,
            @org.springframework.data.repository.query.Param("filiereId") Long filiereId,
            @org.springframework.data.repository.query.Param("niveau") String niveau,
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);
}
