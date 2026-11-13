package com.chatbot.backend.presence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScanRfidRepository extends JpaRepository<ScanRfid, Long> {

    Optional<ScanRfid> findFirstByUidAndResultatInOrderByHorodatageDesc(String uid, List<ScanRfid.Resultat> resultats);

    List<ScanRfid> findTop20ByResultatAndHorodatageAfterOrderByHorodatageDesc(ScanRfid.Resultat resultat,
                                                                              LocalDateTime apres);

    List<ScanRfid> findTop30ByOrderByHorodatageDesc();

    @Modifying
    @Query("UPDATE ScanRfid s SET s.professeur = null WHERE s.professeur.id = :userId")
    int detachProfesseur(@Param("userId") Long userId);
}
