package com.smartlms.repository;

import com.smartlms.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Retrieve all non-deleted messages ordered chronologically.
     */
    List<ChatMessage> findByIsDeletedFalseOrderByCreatedAtAsc();

    /**
     * Retrieve recent active messages for pagination / initial load.
     */
    List<ChatMessage> findTop100ByIsDeletedFalseOrderByCreatedAtDesc();

    /**
     * Retrieve all pinned messages ordered by pinned timestamp descending.
     */
    List<ChatMessage> findByIsDeletedFalseAndIsPinnedTrueOrderByPinnedAtDesc();

    /**
     * Get the latest active message in the conversation.
     */
    Optional<ChatMessage> findTop1ByIsDeletedFalseOrderByCreatedAtDesc();

    /**
     * Count unread messages strictly newer than the user's last read message,
     * excluding messages sent by the user themselves.
     */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.id > :lastReadId AND m.sender.id <> :userId AND m.isDeleted = false")
    long countUnreadMessages(@Param("lastReadId") Long lastReadId, @Param("userId") Long userId);

    /**
     * Search messages containing search term.
     */
    @Query("SELECT m FROM ChatMessage m WHERE LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<ChatMessage> searchMessages(@Param("query") String query);
}
