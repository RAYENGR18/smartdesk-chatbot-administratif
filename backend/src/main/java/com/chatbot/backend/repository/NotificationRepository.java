package com.chatbot.backend.repository;

import com.chatbot.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByTargetAndActiveOrderByCreatedAtDesc(
        Notification.NotifTarget target, boolean active);

    List<Notification> findAllByOrderByCreatedAtDesc();

    /** Notifications pour un étudiant selon sa filière */
    @Query("SELECT n FROM Notification n WHERE n.target = 'STUDENT' AND n.active = true " +
           "AND (n.targetIds = 'ALL' OR n.targetIds LIKE CONCAT('%', :fieldOfStudy, '%')) " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findActiveForStudent(@Param("fieldOfStudy") String fieldOfStudy);

    /** Notifications pour un professeur selon son userId */
    @Query("SELECT n FROM Notification n WHERE n.target = 'PROFESSOR' AND n.active = true " +
           "AND (n.targetIds = 'ALL' OR n.targetIds LIKE CONCAT('%', :userId, '%')) " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findActiveForProfessor(@Param("userId") String userId);
}
