package com.chatbot.backend.presence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PresenceRepository extends JpaRepository<PresenceProfesseur, Long> {

    Optional<PresenceProfesseur> findByProfesseurIdAndDate(Long professeurId, LocalDate date);

    @Query("""
            SELECT p FROM PresenceProfesseur p
            WHERE p.date BETWEEN :debut AND :fin
              AND (:professeurId IS NULL OR p.professeur.id = :professeurId)
            ORDER BY p.date DESC, p.heureArrivee ASC
            """)
    List<PresenceProfesseur> rechercher(@Param("debut") LocalDate debut,
                                        @Param("fin") LocalDate fin,
                                        @Param("professeurId") Long professeurId);

    long countByDate(LocalDate date);

    long countByDateAndHeureDepartIsNull(LocalDate date);

    @Modifying
    @Query("DELETE FROM PresenceProfesseur p WHERE p.professeur.id = :userId")
    int deleteAllByProfesseurId(@Param("userId") Long userId);
}
