package com.smartlms.dto;

import com.smartlms.entity.ChatMessage;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReplySummary {

    private Long id;
    private Long senderId;
    private String senderName;
    private String senderRole;
    private String contentSnippet;

    public static ChatReplySummary fromEntity(ChatMessage message) {
        if (message == null) return null;
        String snippet = message.getContent();
        if (snippet != null && snippet.length() > 80) {
            snippet = snippet.substring(0, 80) + "...";
        }
        return ChatReplySummary.builder()
                .id(message.getId())
                .senderId(message.getSender() != null ? message.getSender().getId() : null)
                .senderName(message.getSender() != null ? message.getSender().getName() : "Unknown")
                .senderRole(message.getSender() != null && message.getSender().getRole() != null ? message.getSender().getRole().name() : null)
                .contentSnippet(snippet)
                .build();
    }
}
