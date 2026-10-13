package com.smartlms.dto;

import com.smartlms.entity.Assignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentResponse {

    private Long id;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private Integer maxMarks;
    private Long courseId;
    private String courseTitle;
    private Long teacherId;
    private String teacherName;
    private int submissionCount;
    private LocalDateTime createdAt;

    public static AssignmentResponse fromEntity(Assignment assignment, int submissionCount) {
        if (assignment == null) {
            return null;
        }
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .dueDate(assignment.getDueDate())
                .maxMarks(assignment.getMaxMarks() != null ? assignment.getMaxMarks() : 100)
                .courseId(assignment.getCourse() != null ? assignment.getCourse().getId() : null)
                .courseTitle(assignment.getCourse() != null ? assignment.getCourse().getTitle() : null)
                .teacherId(assignment.getTeacher() != null ? assignment.getTeacher().getId() : null)
                .teacherName(assignment.getTeacher() != null ? assignment.getTeacher().getName() : null)
                .submissionCount(submissionCount)
                .createdAt(assignment.getCreatedAt())
                .build();
    }
}
