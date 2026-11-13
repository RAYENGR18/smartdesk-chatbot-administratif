package com.chatbot.backend.repository;

import com.chatbot.backend.model.Matiere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatiereRepository extends JpaRepository<Matiere, Long> {
    Optional<Matiere> findByCode(String code);
    Optional<Matiere> findByNom(String nom);
    Boolean existsByCode(String code);

    // Legacy: find by single filiere_id column
    List<Matiere> findByFiliereId(Long filiereId);

    // New: find by many-to-many filieres relationship
    @Query("SELECT m FROM Matiere m JOIN m.filieres f WHERE f.id = :filiereId")
    List<Matiere> findByFilieresId(@Param("filiereId") Long filiereId);

    // Combined: find by either legacy filiere or many-to-many filieres
    @Query("SELECT DISTINCT m FROM Matiere m LEFT JOIN m.filieres f WHERE f.id = :filiereId OR m.filiere.id = :filiereId")
    List<Matiere> findByAnyFiliereId(@Param("filiereId") Long filiereId);
}
