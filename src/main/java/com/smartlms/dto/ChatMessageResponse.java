package com.smartlms.dto;

import com.smartlms.entity.ChatMessage;
import com.smartlms.entity.ChatMessageReaction;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {

    private Long id;
    private Long senderId;
    private String senderName;
    private String senderEmail;
    private String senderRole;
    private String content;
    private String messageType;
    private ChatReplySummary replyTo;
    @Builder.Default
    private List<ChatAttachmentDto> attachments = new ArrayList<>();
    @Builder.Default
    private Map<String, List<String>> reactions = new HashMap<>();
    @Builder.Default
    private Map<String, Integer> reactionCounts = new HashMap<>();
    @Builder.Default
    private List<String> myReactions = new ArrayList<>();
    private boolean isPinned;
    private LocalDateTime pinnedAt;
    private String pinnedByName;
    private LocalDateTime createdAt;

    public static ChatMessageResponse fromEntity(ChatMessage msg, Long currentUserId) {
        if (msg == null) return null;

        Map<String, List<String>> reactionMap = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> userReacted = new ArrayList<>();

        if (msg.getReactions() != null) {
            for (ChatMessageReaction r : msg.getReactions()) {
                String emoji = r.getEmoji();
                String userName = r.getUser() != null ? r.getUser().getName() : "Anonymous";
                reactionMap.computeIfAbsent(emoji, k -> new ArrayList<>()).add(userName);

                if (currentUserId != null && r.getUser() != null && currentUserId.equals(r.getUser().getId())) {
                    if (!userReacted.contains(emoji)) {
                        userReacted.add(emoji);
                    }
                }
            }
            for (Map.Entry<String, List<String>> entry : reactionMap.entrySet()) {
                counts.put(entry.getKey(), entry.getValue().size());
            }
        }

        List<ChatAttachmentDto> attachmentDtos = msg.getAttachments() != null
                ? msg.getAttachments().stream().map(ChatAttachmentDto::fromEntity).collect(Collectors.toList())
                : Collections.emptyList();

        return ChatMessageResponse.builder()
                .id(msg.getId())
                .senderId(msg.getSender() != null ? msg.getSender().getId() : null)
                .senderName(msg.getSender() != null ? msg.getSender().getName() : "Unknown")
                .senderEmail(msg.getSender() != null ? msg.getSender().getEmail() : null)
                .senderRole(msg.getSender() != null && msg.getSender().getRole() != null ? msg.getSender().getRole().name() : null)
                .content(msg.getContent())
                .messageType(msg.getMessageType())
                .replyTo(ChatReplySummary.fromEntity(msg.getReplyTo()))
                .attachments(attachmentDtos)
                .reactions(reactionMap)
                .reactionCounts(counts)
                .myReactions(userReacted)
                .isPinned(msg.isPinned())
                .pinnedAt(msg.getPinnedAt())
                .pinnedByName(msg.getPinnedBy() != null ? msg.getPinnedBy().getName() : null)
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
