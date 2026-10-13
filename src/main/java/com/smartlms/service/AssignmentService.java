package com.smartlms.service;

import com.smartlms.dto.AssignmentRequest;
import com.smartlms.dto.AssignmentResponse;
import com.smartlms.dto.SubmissionResponse;
import com.smartlms.entity.Assignment;
import com.smartlms.entity.Course;
import java.time.LocalDateTime;
import com.smartlms.entity.Role;
import com.smartlms.entity.Submission;
import com.smartlms.entity.User;
import com.smartlms.exception.BadRequestException;
import com.smartlms.exception.ResourceNotFoundException;
import com.smartlms.repository.AssignmentRepository;
import com.smartlms.repository.CourseRepository;
import com.smartlms.repository.SubmissionRepository;
import com.smartlms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    private final Path uploadDir = Paths.get("uploads", "submissions").toAbsolutePath().normalize();

    private void ensureUploadDirExists() {
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
        } catch (IOException e) {
            log.error("Failed to create upload directory", e);
        }
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAllAssignments() {
        return assignmentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByCourse(Long courseId) {
        return assignmentRepository.findByCourseId(courseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignmentById(Long id) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", id));
        return mapToResponse(assignment);
    }

    @Transactional
    public AssignmentResponse createAssignment(AssignmentRequest request, String teacherEmail) {
        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", teacherEmail));

        if (teacher.getRole() != Role.ROLE_TEACHER && teacher.getRole() != Role.ROLE_ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Only faculty members and administrators can create assignments");
        }

        if (request.getTitle() == null || request.getTitle().trim().isBlank()) {
            throw new BadRequestException("Assignment title is required");
        }

        if (request.getDueDate() == null) {
            throw new BadRequestException("Due date (deadline) is required");
        }

        if (request.getCourseId() == null) {
            throw new BadRequestException("Associated course is required");
        }

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));

        if (!course.isActive()) {
            throw new BadRequestException("Cannot create assignments for an inactive course");
        }

        Assignment assignment = Assignment.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : "")
                .dueDate(request.getDueDate())
                .maxMarks(100)
                .course(course)
                .teacher(teacher)
                .build();

        Assignment saved = assignmentRepository.save(assignment);
        log.info("Created assignment '{}' (id: {}, maxMarks: {}) by teacher '{}'", saved.getTitle(), saved.getId(), saved.getMaxMarks(), teacherEmail);
        return mapToResponse(saved);
    }

    @Transactional
    public SubmissionResponse submitAssignment(Long assignmentId, MultipartFile file, String remarks, String studentEmail) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required for assignment submission");
        }

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", assignmentId));

        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", studentEmail));

        ensureUploadDirExists();

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "submission.pdf";
        }

        String safeStoredFilename = UUID.randomUUID().toString() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path targetPath = uploadDir.resolve(safeStoredFilename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save submission file", e);
            throw new RuntimeException("Could not save uploaded file: " + e.getMessage(), e);
        }

        String fileUrl = "/api/assignments/submissions/files/" + safeStoredFilename;

        // Check if student already submitted — update or create
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId())
                .orElseGet(() -> Submission.builder()
                        .assignment(assignment)
                        .student(student)
                        .build());

        submission.setFileName(safeStoredFilename);
        submission.setOriginalFileName(originalFilename);
        submission.setFileUrl(fileUrl);
        submission.setFileSize(file.getSize());
        submission.setFileType(file.getContentType());
        submission.setRemarks(remarks);
        if (submission.getStatus() == null || !submission.getStatus().equals("GRADED")) {
            submission.setStatus("SUBMITTED");
        }

        Submission saved = submissionRepository.save(submission);
        log.info("Student '{}' submitted file '{}' for assignment '{}'", studentEmail, originalFilename, assignment.getTitle());
        return SubmissionResponse.fromEntity(saved);
    }

    @Transactional
    public SubmissionResponse gradeSubmission(Long submissionId, Double marks, String feedback, String teacherEmail) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", "id", submissionId));

        User user = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", teacherEmail));

        // Check ownership or admin
        boolean isOwner = submission.getAssignment() != null &&
                submission.getAssignment().getTeacher() != null &&
                submission.getAssignment().getTeacher().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == com.smartlms.entity.Role.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            throw new com.smartlms.exception.BadRequestException("You can only grade submissions for your own assignments");
        }

        if (marks == null) {
            throw new BadRequestException("Marks awarded is required");
        }
        double maxAllowed = submission.getAssignment() != null && submission.getAssignment().getMaxMarks() != null
                ? submission.getAssignment().getMaxMarks().doubleValue()
                : 100.0;
        if (marks < 0.0 || marks > maxAllowed) {
            throw new BadRequestException("Marks must be between 0 and " + (int) maxAllowed);
        }

        submission.setMarksObtained(marks);
        submission.setFeedback(feedback != null ? feedback.trim() : "");
        submission.setStatus("GRADED");
        submission.setGradedAt(LocalDateTime.now());

        Submission saved = submissionRepository.save(submission);
        log.info("Teacher '{}' graded submission {} with marks {}", teacherEmail, submissionId, marks);
        return SubmissionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getSubmissionsForAssignment(Long assignmentId, String teacherEmail) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new ResourceNotFoundException("Assignment", "id", assignmentId);
        }
        return submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId).stream()
                .map(SubmissionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getMySubmission(Long assignmentId, String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", studentEmail));

        return submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId())
                .map(SubmissionResponse::fromEntity)
                .orElse(null);
    }

    public Resource loadFileAsResource(String filename) {
        try {
            ensureUploadDirExists();
            Path filePath = uploadDir.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("File", "name", filename);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File", "name", filename);
        }
    }

    private AssignmentResponse mapToResponse(Assignment assignment) {
        int submissionCount = submissionRepository.countByAssignmentId(assignment.getId());
        return AssignmentResponse.fromEntity(assignment, submissionCount);
    }
}
