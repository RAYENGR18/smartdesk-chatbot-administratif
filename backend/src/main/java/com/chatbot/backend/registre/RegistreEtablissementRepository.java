package com.chatbot.backend.registre;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RegistreEtablissementRepository extends JpaRepository<RegistreEtablissement, Long> {

    Optional<RegistreEtablissement> findByCin(String cin);

    long countByType(TypePersonne type);

    @Query("""
            SELECT r FROM RegistreEtablissement r
            WHERE (:type IS NULL OR r.type = :type)
              AND (:q = '' OR LOWER(r.cin) LIKE CONCAT('%', :q, '%')
                   OR LOWER(r.nom) LIKE CONCAT('%', :q, '%')
                   OR LOWER(r.prenom) LIKE CONCAT('%', :q, '%')
                   OR LOWER(COALESCE(r.filiere, '')) LIKE CONCAT('%', :q, '%'))
            ORDER BY r.nom, r.prenom
            """)
    Page<RegistreEtablissement> rechercher(@Param("q") String q,
                                           @Param("type") TypePersonne type,
                                           Pageable pageable);
}
