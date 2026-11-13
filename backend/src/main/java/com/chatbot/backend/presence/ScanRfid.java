package com.chatbot.backend.presence;

import com.chatbot.backend.model.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Journal de tous les passages de badge (y compris badges inconnus et doublons). */
@Entity
@Table(name = "scans_rfid", indexes = {
        @Index(name = "idx_scan_uid_date", columnList = "uid, horodatage"),
        @Index(name = "idx_scan_resultat", columnList = "resultat")})
@Data
@NoArgsConstructor
public class ScanRfid {

    public enum Resultat { ARRIVEE, DEPART, DEJA_CLOTURE, DOUBLON, INCONNU }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String uid;

    @Column(nullable = false)
    private LocalDateTime horodatage;

    @Column(length = 50)
    private String lecteur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Resultat resultat;

    @ManyToOne
    @JoinColumn(name = "professeur_id")
    private User professeur;
}
