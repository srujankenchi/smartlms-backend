package com.smartlms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReactionRequest {

    @NotBlank(message = "Emoji is required")
    @Size(max = 32, message = "Emoji character string too long")
    private String emoji;
}
