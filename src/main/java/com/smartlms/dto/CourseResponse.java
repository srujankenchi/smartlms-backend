package com.smartlms.dto;

import com.smartlms.entity.Course;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseResponse {

    private Long id;
    private String code;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String category;
    private boolean published;
    private boolean active;
    private Long teacherId;
    private String teacherName;
    private int lessonCount;
    private LocalDateTime createdAt;

    public static CourseResponse fromEntity(Course course, int lessonCount) {
        if (course == null) {
            return null;
        }
        return CourseResponse.builder()
                .id(course.getId())
                .code(course.getCode())
                .title(course.getTitle())
                .description(course.getDescription())
                .thumbnailUrl(course.getThumbnailUrl())
                .category(course.getCategory())
                .published(course.isPublished())
                .active(course.isActive())
                .teacherId(course.getTeacher() != null ? course.getTeacher().getId() : null)
                .teacherName(course.getTeacher() != null ? course.getTeacher().getName() : null)
                .lessonCount(lessonCount)
                .createdAt(course.getCreatedAt())
                .build();
    }
}
