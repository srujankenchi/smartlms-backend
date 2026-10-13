package com.smartlms.service;

import com.smartlms.dto.*;

import com.smartlms.entity.*;

import com.smartlms.exception.BadRequestException;

import com.smartlms.exception.ResourceNotFoundException;

import com.smartlms.repository.*;

import com.smartlms.websocket.ChatWebSocketHandler;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.Resource;

import org.springframework.core.io.UrlResource;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.net.MalformedURLException;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.Paths;

import java.nio.file.StandardCopyOption;

import java.time.LocalDateTime;

import java.util.*;

import java.util.stream.Collectors;

@Slf4j

@Service

@RequiredArgsConstructor

public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    private final ChatAttachmentRepository chatAttachmentRepository;

    private final ChatMessageReactionRepository reactionRepository;

    private final ChatUserReadRepository userReadRepository;

    private final UserRepository userRepository;

    private final ChatWebSocketHandler webSocketHandler;

    private final Optional<SimpMessagingTemplate> messagingTemplate;

    private final Path uploadDir = Paths.get("uploads", "chat");

    private void ensureUploadDirExists() {

        try {

            if (!Files.exists(uploadDir)) {

                Files.createDirectories(uploadDir);

            }

        } catch (IOException e) {

            log.error("Failed to create chat attachments directory", e);

            throw new RuntimeException("Could not initialize chat storage: " + e.getMessage(), e);

        }

    }

    /**

     * Retrieve all active messages ordered chronologically for the chat room.

     */

    @Transactional(readOnly = true)

    public List<ChatMessageResponse> getMessages(String userEmail) {

        User currentUser = getUserByEmail(userEmail);

        List<ChatMessage> messages = chatMessageRepository.findByIsDeletedFalseOrderByCreatedAtAsc();

        return messages.stream()

                .map(m -> ChatMessageResponse.fromEntity(m, currentUser.getId()))

                .collect(Collectors.toList());

    }

    /**

     * Post a new message with optional reply and attachments.

     */

    @Transactional

    public ChatMessageResponse sendMessage(ChatMessageRequest request, MultipartFile optionalFile, String userEmail) {

        User sender = getUserByEmail(userEmail);

        if ((request == null || request.getContent() == null || request.getContent().trim().isBlank())

                && (optionalFile == null || optionalFile.isEmpty())) {

            throw new BadRequestException("Message content or an attachment is required");

        }

        String content = request != null && request.getContent() != null ? request.getContent().trim() : "";

        if (content.isBlank() && optionalFile != null) {

            content = "Shared attachment: " + optionalFile.getOriginalFilename();

        }

        ChatMessage replyTo = null;

        if (request != null && request.getReplyToId() != null) {

            replyTo = chatMessageRepository.findById(request.getReplyToId())

                    .filter(m -> !m.isDeleted())

                    .orElse(null);

        }

        String messageType = "TEXT";

        ChatMessage message = ChatMessage.builder()

                .sender(sender)

                .content(content)

                .messageType(messageType)

                .replyTo(replyTo)

                .isPinned(false)

                .isDeleted(false)

                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);

        // Process pre-uploaded attachment IDs if present.
        // Keep a stable reference for attachment processing because savedMessage
        // is reassigned later after its attachment list and message type are updated.
        List<ChatAttachment> attachedList = new ArrayList<>();
        final ChatMessage messageForAttachments = savedMessage;

        if (request != null && request.getAttachmentIds() != null && !request.getAttachmentIds().isEmpty()) {
            for (Long attId : request.getAttachmentIds()) {
                Optional<ChatAttachment> attachmentOptional = chatAttachmentRepository.findById(attId);
                if (attachmentOptional.isPresent()) {
                    ChatAttachment att = attachmentOptional.get();
                    att.setMessage(messageForAttachments);
                    ChatAttachment updatedAttachment = chatAttachmentRepository.save(att);
                    attachedList.add(updatedAttachment);
                }
            }
        }

        // Process attached file in this multipart request if present

        if (optionalFile != null && !optionalFile.isEmpty()) {

            ChatAttachment att = storeFile(optionalFile, savedMessage);

            attachedList.add(att);

        }

        if (!attachedList.isEmpty()) {

            savedMessage.setAttachments(attachedList);

            boolean hasImage = attachedList.stream().anyMatch(a -> a.getFileType() != null && a.getFileType().startsWith("image/"));

            savedMessage.setMessageType(hasImage ? "IMAGE" : "FILE");

            savedMessage = chatMessageRepository.save(savedMessage);

        }

        // Advance sender's read status to this message automatically

        updateUserReadStatus(sender, savedMessage.getId());

        ChatMessageResponse response = ChatMessageResponse.fromEntity(savedMessage, sender.getId());

        // Broadcast new message via WebSocket and STOMP

        broadcastEvent("NEW_MESSAGE", response);

        log.info("Chat message {} posted by {} ({})", savedMessage.getId(), sender.getEmail(), sender.getRole());

        return response;

    }

    /**

     * Upload an attachment file for chat.

     */

    @Transactional

    public ChatAttachmentDto uploadAttachment(MultipartFile file, String userEmail) {

        getUserByEmail(userEmail);

        ChatAttachment att = storeFile(file, null);

        return ChatAttachmentDto.fromEntity(att);

    }

    private ChatAttachment storeFile(MultipartFile file, ChatMessage message) {

        if (file == null || file.isEmpty()) {

            throw new BadRequestException("Uploaded file cannot be empty");

        }

        // Limit file size to 20MB

        if (file.getSize() > 20 * 1024 * 1024) {

            throw new BadRequestException("File size exceeds 20MB maximum allowed limit");

        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {

            originalFilename = "attachment";

        }

        // Validate extension against safe educational types

        String lowerName = originalFilename.toLowerCase();

        boolean validExt = lowerName.endsWith(".pdf") || lowerName.endsWith(".png") ||

                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||

                lowerName.endsWith(".gif") || lowerName.endsWith(".txt") ||

                lowerName.endsWith(".docx") || lowerName.endsWith(".doc") ||

                lowerName.endsWith(".xlsx") || lowerName.endsWith(".pptx") ||

                lowerName.endsWith(".zip");

        if (!validExt) {

            throw new BadRequestException("File format not supported. Allowed: PDF, PNG, JPG, GIF, TXT, DOCX, XLSX, PPTX, ZIP");

        }

        ensureUploadDirExists();

        String safeStoredFilename = UUID.randomUUID().toString() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        Path targetPath = uploadDir.resolve(safeStoredFilename);

        try {

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {

            log.error("Failed to save chat attachment", e);

            throw new RuntimeException("Could not save uploaded attachment: " + e.getMessage(), e);

        }

        String fileUrl = "/api/chat/attachments/file/" + safeStoredFilename;

        ChatAttachment attachment = ChatAttachment.builder()

                .message(message)

                .fileName(originalFilename)

                .storedFileName(safeStoredFilename)

                .fileUrl(fileUrl)

                .fileType(file.getContentType())

                .fileSize(file.getSize())

                .build();

        return chatAttachmentRepository.save(attachment);

    }

    /**

     * Download or view an attachment file.

     */

    @Transactional(readOnly = true)

    public Resource getAttachmentResource(String storedFileName) {

        ensureUploadDirExists();

        Path filePath = uploadDir.resolve(storedFileName).normalize();

        if (!filePath.startsWith(uploadDir.normalize())) {

            throw new BadRequestException("Invalid file path");

        }

        try {

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {

                return resource;

            } else {

                throw new ResourceNotFoundException("ChatAttachment", "storedFileName", storedFileName);

            }

        } catch (MalformedURLException e) {

            throw new RuntimeException("Error resolving attachment: " + e.getMessage(), e);

        }

    }

    /**

     * Download attachment by ID.

     */

    @Transactional(readOnly = true)

    public Map.Entry<ChatAttachment, Resource> getAttachmentById(Long attachmentId) {

        ChatAttachment attachment = chatAttachmentRepository.findById(attachmentId)

                .orElseThrow(() -> new ResourceNotFoundException("ChatAttachment", "id", attachmentId));

        Resource resource = getAttachmentResource(attachment.getStoredFileName());

        return Map.entry(attachment, resource);

    }

    /**

     * Toggle an emoji reaction on a message.

     */

    @Transactional

    public ChatMessageResponse toggleReaction(Long messageId, String emoji, String userEmail) {

        User user = getUserByEmail(userEmail);

        ChatMessage message = chatMessageRepository.findById(messageId)

                .filter(m -> !m.isDeleted())

                .orElseThrow(() -> new ResourceNotFoundException("ChatMessage", "id", messageId));

        if (emoji == null || emoji.trim().isBlank()) {

            throw new BadRequestException("Emoji cannot be blank");

        }

        String cleanEmoji = emoji.trim();

        Optional<ChatMessageReaction> existing = reactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, user.getId(), cleanEmoji);

        if (existing.isPresent()) {

            reactionRepository.delete(existing.get());

            message.getReactions().remove(existing.get());

        } else {

            ChatMessageReaction newReaction = ChatMessageReaction.builder()

                    .message(message)

                    .user(user)

                    .emoji(cleanEmoji)

                    .build();

            ChatMessageReaction saved = reactionRepository.save(newReaction);

            message.getReactions().add(saved);

        }

        ChatMessage updated = chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.fromEntity(updated, user.getId());

        broadcastEvent("REACTION_UPDATED", response);

        return response;

    }

    /**

     * Toggle pinned status of a message (Teacher or Admin only).

     */

    @Transactional

    public ChatMessageResponse togglePin(Long messageId, String userEmail) {

        User user = getUserByEmail(userEmail);

        if (user.getRole() != Role.ROLE_TEACHER && user.getRole() != Role.ROLE_ADMIN) {

            throw new org.springframework.security.access.AccessDeniedException("Only faculty members and administrators can pin or unpin messages");

        }

        ChatMessage message = chatMessageRepository.findById(messageId)

                .filter(m -> !m.isDeleted())

                .orElseThrow(() -> new ResourceNotFoundException("ChatMessage", "id", messageId));

        boolean newPinned = !message.isPinned();

        message.setPinned(newPinned);

        if (newPinned) {

            message.setPinnedAt(LocalDateTime.now());

            message.setPinnedBy(user);

        } else {

            message.setPinnedAt(null);

            message.setPinnedBy(null);

        }

        ChatMessage saved = chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.fromEntity(saved, user.getId());

        broadcastEvent("PIN_UPDATED", response);

        log.info("Message {} pin status set to {} by {}", messageId, newPinned, user.getEmail());

        return response;

    }

    /**

     * Delete a message (Sender, Teacher, or Admin).

     */

    @Transactional

    public void deleteMessage(Long messageId, String userEmail) {

        User user = getUserByEmail(userEmail);

        ChatMessage message = chatMessageRepository.findById(messageId)

                .filter(m -> !m.isDeleted())

                .orElseThrow(() -> new ResourceNotFoundException("ChatMessage", "id", messageId));

        boolean isSender = message.getSender() != null && message.getSender().getId().equals(user.getId());

        boolean isTeacherOrAdmin = user.getRole() == Role.ROLE_TEACHER || user.getRole() == Role.ROLE_ADMIN;

        if (!isSender && !isTeacherOrAdmin) {

            throw new org.springframework.security.access.AccessDeniedException("You can only delete your own messages");

        }

        message.setDeleted(true);

        chatMessageRepository.save(message);

        broadcastEvent("MESSAGE_DELETED", Map.of("messageId", messageId));

        log.info("Message {} marked deleted by {}", messageId, user.getEmail());

    }

    /**

     * Get unread count and latest message preview for navbar badge.

     */

    @Transactional(readOnly = true)

    public ChatUnreadCountResponse getUnreadCount(String userEmail) {

        User user = getUserByEmail(userEmail);

        Long lastReadId = userReadRepository.findByUserId(user.getId())

                .map(ChatUserRead::getLastReadMessageId)

                .orElse(0L);

        long unreadCount = chatMessageRepository.countUnreadMessages(lastReadId, user.getId());

        Optional<ChatMessage> latestOpt = chatMessageRepository.findTop1ByIsDeletedFalseOrderByCreatedAtDesc();

        String snippet = null;

        LocalDateTime latestTime = null;

        String senderName = null;

        String senderRole = null;

        Long latestId = null;

        if (latestOpt.isPresent()) {

            ChatMessage latest = latestOpt.get();

            latestId = latest.getId();

            snippet = latest.getContent();

            if (snippet != null && snippet.length() > 60) {

                snippet = snippet.substring(0, 60) + "...";

            }

            latestTime = latest.getCreatedAt();

            if (latest.getSender() != null) {

                senderName = latest.getSender().getName();

                senderRole = latest.getSender().getRole() != null ? latest.getSender().getRole().name() : null;

            }

        }

        return ChatUnreadCountResponse.builder()

                .unreadCount(unreadCount)

                .lastMessageId(latestId)

                .lastMessageSnippet(snippet)

                .lastMessageTime(latestTime)

                .lastSenderName(senderName)

                .lastSenderRole(senderRole)

                .build();

    }

    /**

     * Mark messages as read up to the given message ID.

     */

    @Transactional

    public void markAsRead(Long upToMessageId, String userEmail) {

        User user = getUserByEmail(userEmail);

        if (upToMessageId == null) {

            chatMessageRepository.findTop1ByIsDeletedFalseOrderByCreatedAtDesc()

                    .ifPresent(m -> updateUserReadStatus(user, m.getId()));

        } else {

            updateUserReadStatus(user, upToMessageId);

        }

    }

    private void updateUserReadStatus(User user, Long messageId) {

        if (messageId == null) return;

        ChatUserRead read = userReadRepository.findByUserId(user.getId())

                .orElseGet(() -> ChatUserRead.builder()

                        .user(user)

                        .lastReadMessageId(0L)

                        .lastReadAt(LocalDateTime.now())

                        .build());

        if (messageId > read.getLastReadMessageId()) {

            read.setLastReadMessageId(messageId);

            read.setLastReadAt(LocalDateTime.now());

            userReadRepository.save(read);

        }

    }

    /**

     * Retrieve all pinned messages.

     */

    @Transactional(readOnly = true)

    public List<ChatMessageResponse> getPinnedMessages(String userEmail) {

        User user = getUserByEmail(userEmail);

        return chatMessageRepository.findByIsDeletedFalseAndIsPinnedTrueOrderByPinnedAtDesc().stream()

                .map(m -> ChatMessageResponse.fromEntity(m, user.getId()))

                .collect(Collectors.toList());

    }

    /**

     * Search messages containing search term.

     */

    @Transactional(readOnly = true)

    public List<ChatMessageResponse> searchMessages(String query, String userEmail) {

        User user = getUserByEmail(userEmail);

        if (query == null || query.trim().isBlank()) {

            return Collections.emptyList();

        }

        return chatMessageRepository.searchMessages(query.trim()).stream()

                .map(m -> ChatMessageResponse.fromEntity(m, user.getId()))

                .collect(Collectors.toList());

    }

    /**

     * Get group details, faculty supervisor, and member list.

     */

    @Transactional(readOnly = true)

    public ChatGroupInfoResponse getGroupInfo(String userEmail) {

        List<User> allUsers = userRepository.findAll();

        List<ChatMemberDto> memberDtos = allUsers.stream()

                .map(ChatMemberDto::fromUser)

                .collect(Collectors.toList());

        User teacher = allUsers.stream()

                .filter(u -> u.getRole() == Role.ROLE_TEACHER)

                .findFirst()

                .orElse(null);

        long pinnedCount = chatMessageRepository.findByIsDeletedFalseAndIsPinnedTrueOrderByPinnedAtDesc().size();

        String teacherName = teacher != null ? teacher.getName() : "Faculty Advisor";

        String teacherEmail = teacher != null ? teacher.getEmail() : "faculty@smartlms.edu";

        int totalMembers = allUsers.size();

        String subtitle = "1 Teacher · " + Math.max(1, totalMembers - 1) + " Class Students";

        return ChatGroupInfoResponse.builder()

                .groupName("CSE Class Group")

                .subtitle(subtitle)

                .teacherName(teacherName)

                .teacherEmail(teacherEmail)

                .totalMembers(totalMembers)

                .pinnedCount((int) pinnedCount)

                .members(memberDtos)

                .build();

    }

    private User getUserByEmail(String email) {

        return userRepository.findByEmail(email)

                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

    }

    private void broadcastEvent(String eventType, Object data) {

        // Native WebSocket

        webSocketHandler.broadcast(eventType, data);

        // STOMP Broker

        messagingTemplate.ifPresent(template -> {

            try {

                template.convertAndSend("/topic/chat", Map.of(

                        "type", eventType,

                        "data", data,

                        "timestamp", System.currentTimeMillis()

                ));

            } catch (Exception e) {

                log.warn("STOMP broadcast failed: {}", e.getMessage());

            }

        });

    }

}
