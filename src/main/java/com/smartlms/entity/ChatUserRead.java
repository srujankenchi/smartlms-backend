package com.smartlms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity tracking the last read message ID and timestamp per user.
 * Enables fast unread-count calculation and persistent read status in MySQL.
 */
@Entity
@Table(name = "chat_user_reads", uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_user_read_user", columnNames = {"user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatUserRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "last_read_message_id", nullable = false)
    @Builder.Default
    private Long lastReadMessageId = 0L;

    @Column(name = "last_read_at", nullable = false)
    private LocalDateTime lastReadAt;
}
