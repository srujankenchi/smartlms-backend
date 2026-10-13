package com.smartlms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessageRequest {

    @NotBlank(message = "Message text cannot be blank")
    @Size(max = 5000, message = "Message text cannot exceed 5000 characters")
    private String content;

    private Long replyToId;

    private List<Long> attachmentIds;
}
