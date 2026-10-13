package com.smartlms.dto;

import com.smartlms.entity.User;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMemberDto {

    private Long id;
    private String name;
    private String email;
    private String role;

    public static ChatMemberDto fromUser(User user) {
        if (user == null) return null;
        return ChatMemberDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : "STUDENT")
                .build();
    }
}
