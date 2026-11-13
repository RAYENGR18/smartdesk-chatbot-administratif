package com.chatbot.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * STUDENT → notification pour les étudiants (ciblage par filière)
     * PROFESSOR → notification pour les professeurs (ciblage par userId)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotifTarget target;

    /**
     * "ALL" → tous les utilisateurs du type cible
     * Pour STUDENT  : liste de fieldOfStudy séparés par virgule, ex: "LIG,LSG"
     * Pour PROFESSOR: liste de userId séparés par virgule, ex: "3,7,12"
     */
    @Column(nullable = false)
    private String targetIds = "ALL";

    /** Priorité visuelle : INFO, WARNING, URGENT */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotifPriority priority = NotifPriority.INFO;

    private boolean active = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum NotifTarget   { STUDENT, PROFESSOR }
    public enum NotifPriority { INFO, WARNING, URGENT }
}
