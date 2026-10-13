package com.smartlms.dto;

import com.smartlms.entity.Lesson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonResponse {

    private Long id;
    private String title;
    private String description;
    private String content;
    private String videoUrl;
    private Integer orderIndex;
    private Long courseId;
    private LocalDateTime createdAt;

    public static LessonResponse fromEntity(Lesson lesson) {
        if (lesson == null) {
            return null;
        }
        return LessonResponse.builder()
                .id(lesson.getId())
                .title(lesson.getTitle())
                .description(lesson.getDescription())
                .content(lesson.getContent())
                .videoUrl(com.smartlms.util.YouTubeUtil.toEmbedUrl(lesson.getVideoUrl()))
                .orderIndex(lesson.getOrderIndex())
                .courseId(lesson.getCourse() != null ? lesson.getCourse().getId() : null)
                .createdAt(lesson.getCreatedAt())
                .build();
    }
}
