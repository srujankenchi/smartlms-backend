package com.smartlms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing an emoji reaction on a chat message.
 * Stored in `chat_reactions` table with unique constraint (message_id, user_id, emoji).
 */
@Entity
@Table(name = "chat_reactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reaction_message_user_emoji", columnNames = {"message_id", "user_id", "emoji"})
}, indexes = {
        @Index(name = "idx_reaction_message_id", columnList = "message_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 32)
    private String emoji;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
