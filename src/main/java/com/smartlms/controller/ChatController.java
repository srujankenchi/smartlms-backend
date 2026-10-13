package com.smartlms.controller;

import com.smartlms.dto.*;
import com.smartlms.entity.ChatAttachment;
import com.smartlms.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST API for SmartLMS Class Connect group messaging.
 * Accessible by all authenticated roles (ADMIN, TEACHER, STUDENT).
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(Authentication auth) {
        List<ChatMessageResponse> messages = chatService.getMessages(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Messages retrieved successfully", messages));
    }

    /**
     * Post message via standard JSON payload.
     */
    @PostMapping("/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            Authentication auth) {
        ChatMessageResponse response = chatService.sendMessage(request, null, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent successfully", response));
    }

    /**
     * Post message with direct file upload via multipart/form-data.
     */
    @PostMapping(value = "/messages/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessageMultipart(
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "replyToId", required = false) Long replyToId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication auth) {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .content(content)
                .replyToId(replyToId)
                .build();
        ChatMessageResponse response = chatService.sendMessage(request, file, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message with attachment sent successfully", response));
    }

    /**
     * Upload an attachment separately.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatAttachmentDto>> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        ChatAttachmentDto attachment = chatService.uploadAttachment(file, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded successfully", attachment));
    }

    /**
     * Stream or preview an attachment file inline (e.g. image, pdf).
     */
    @GetMapping("/attachments/file/{storedFileName}")
    public ResponseEntity<Resource> viewAttachmentFile(@PathVariable String storedFileName) {
        Resource resource = chatService.getAttachmentResource(storedFileName);
        String contentType = "application/octet-stream";
        String lowerName = storedFileName.toLowerCase();
        if (lowerName.endsWith(".png")) contentType = "image/png";
        else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) contentType = "image/jpeg";
        else if (lowerName.endsWith(".gif")) contentType = "image/gif";
        else if (lowerName.endsWith(".pdf")) contentType = "application/pdf";
        else if (lowerName.endsWith(".txt")) contentType = "text/plain";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /**
     * Download attachment by ID with proper original filename.
     */
    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        Map.Entry<ChatAttachment, Resource> entry = chatService.getAttachmentById(id);
        ChatAttachment attachment = entry.getKey();
        Resource resource = entry.getValue();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getFileType() != null ? attachment.getFileType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"")
                .body(resource);
    }

    /**
     * Toggle emoji reaction.
     */
    @PostMapping("/messages/{id}/react")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> reactToMessage(
            @PathVariable Long id,
            @Valid @RequestBody ChatReactionRequest request,
            Authentication auth) {
        ChatMessageResponse response = chatService.toggleReaction(id, request.getEmoji(), auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Reaction updated", response));
    }

    /**
     * Pin or unpin message (Faculty and Admin).
     */
    @PostMapping("/messages/{id}/pin")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> togglePin(
            @PathVariable Long id,
            Authentication auth) {
        ChatMessageResponse response = chatService.togglePin(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Message pin status updated", response));
    }

    /**
     * Delete message (Sender or Moderator).
     */
    @DeleteMapping("/messages/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable Long id,
            Authentication auth) {
        chatService.deleteMessage(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Message deleted successfully", null));
    }

    /**
     * Unread message count and preview for navbar.
     */
    @GetMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatUnreadCountResponse>> getUnreadCount(Authentication auth) {
        ChatUnreadCountResponse response = chatService.getUnreadCount(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved", response));
    }

    /**
     * Mark messages as read.
     */
    @PostMapping("/mark-read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @RequestParam(value = "messageId", required = false) Long messageId,
            Authentication auth) {
        chatService.markAsRead(messageId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    /**
     * Pinned messages list.
     */
    @GetMapping("/pinned")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getPinnedMessages(Authentication auth) {
        List<ChatMessageResponse> pinned = chatService.getPinnedMessages(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Pinned messages retrieved", pinned));
    }

    /**
     * Group info (member counts, teacher, members).
     */
    @GetMapping("/group-info")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatGroupInfoResponse>> getGroupInfo(Authentication auth) {
        ChatGroupInfoResponse info = chatService.getGroupInfo(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Group info retrieved", info));
    }

    /**
     * Search messages.
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> searchMessages(
            @RequestParam("q") String query,
            Authentication auth) {
        List<ChatMessageResponse> results = chatService.searchMessages(query, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Search results retrieved", results));
    }
}
