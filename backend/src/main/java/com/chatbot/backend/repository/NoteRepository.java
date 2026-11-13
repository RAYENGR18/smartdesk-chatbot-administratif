package com.chatbot.backend.repository;

import com.chatbot.backend.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByEtudiantId(Long etudiantId);
    List<Note> findByMatiereId(Long matiereId);
    List<Note> findByEtudiantIdAndMatiereId(Long etudiantId, Long matiereId);
    void deleteByEtudiantId(Long etudiantId);

    @Modifying
    @Query("DELETE FROM Note n WHERE n.etudiant.id = :userId")
    int deleteAllByEtudiantId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Note n SET n.professeur = null WHERE n.professeur.id = :userId")
    int detachProfesseur(@Param("userId") Long userId);
}
