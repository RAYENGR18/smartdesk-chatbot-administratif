package com.chatbot.backend.presence;

import com.chatbot.backend.model.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/** Présence d'un professeur pour une journée : arrivée (1er passage) et départ (2e passage). */
@Entity
@Table(name = "presences_professeurs",
        uniqueConstraints = @UniqueConstraint(name = "uk_presence_prof_jour", columnNames = {"professeur_id", "date_presence"}))
@Data
@NoArgsConstructor
public class PresenceProfesseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "professeur_id", nullable = false)
    private User professeur;

    @Column(name = "date_presence", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime heureArrivee;

    private LocalTime heureDepart;

    /** Identifiant du lecteur ("ENTREE", "SIMULATION"...). */
    @Column(length = 50)
    private String lecteur;
}
