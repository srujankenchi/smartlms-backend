package com.smartlms.dto;

import com.smartlms.entity.ChatAttachment;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatAttachmentDto {

    private Long id;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;

    public static ChatAttachmentDto fromEntity(ChatAttachment entity) {
        if (entity == null) return null;
        return ChatAttachmentDto.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .fileUrl(entity.getFileUrl())
                .fileType(entity.getFileType())
                .fileSize(entity.getFileSize())
                .build();
    }
}
