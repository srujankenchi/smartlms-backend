package com.smartlms.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatGroupInfoResponse {

    private String groupName;
    private String subtitle;
    private String teacherName;
    private String teacherEmail;
    private int totalMembers;
    private int pinnedCount;
    @Builder.Default
    private List<ChatMemberDto> members = new ArrayList<>();
}
