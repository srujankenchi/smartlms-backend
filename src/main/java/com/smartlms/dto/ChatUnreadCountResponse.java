package com.smartlms.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatUnreadCountResponse {

    private long unreadCount;
    private Long lastMessageId;
    private String lastMessageSnippet;
    private LocalDateTime lastMessageTime;
    private String lastSenderName;
    private String lastSenderRole;
}
