package com.smartlms.dto;

import com.smartlms.entity.Submission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionResponse {

    private Long id;
    private Long assignmentId;
    private String assignmentTitle;
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private String fileName;
    private String originalFileName;
    private String fileUrl;
    private String remarks;
    private Long fileSize;
    private String fileType;
    private String content;
    private String status;
    private Double marksObtained;
    private String feedback;
    private LocalDateTime gradedAt;
    private LocalDateTime submittedAt;

    public static SubmissionResponse fromEntity(Submission submission) {
        if (submission == null) {
            return null;
        }
        return SubmissionResponse.builder()
                .id(submission.getId())
                .assignmentId(submission.getAssignment() != null ? submission.getAssignment().getId() : null)
                .assignmentTitle(submission.getAssignment() != null ? submission.getAssignment().getTitle() : null)
                .studentId(submission.getStudent() != null ? submission.getStudent().getId() : null)
                .studentName(submission.getStudent() != null ? submission.getStudent().getName() : null)
                .studentEmail(submission.getStudent() != null ? submission.getStudent().getEmail() : null)
                .fileName(submission.getFileName())
                .originalFileName(submission.getOriginalFileName())
                .fileUrl(submission.getFileUrl())
                .fileSize(submission.getFileSize())
                .fileType(submission.getFileType())
                .content(submission.getContent())
                .remarks(submission.getRemarks())
                .status(submission.getStatus())
                .marksObtained(submission.getMarksObtained())
                .feedback(submission.getFeedback())
                .gradedAt(submission.getGradedAt())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }
}
