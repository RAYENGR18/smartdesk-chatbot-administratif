package com.chatbot.backend.repository;

import com.chatbot.backend.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByUserIdOrderByCreatedAtAsc(Long userId);

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Query("SELECT m FROM ChatMessage m WHERE m.user.id = :userId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    long countByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(c.createdAt) FROM ChatMessage c WHERE c.user.id = :userId")
    java.time.LocalDateTime findDernierMessage(@org.springframework.data.repository.query.Param("userId") Long userId);
}
