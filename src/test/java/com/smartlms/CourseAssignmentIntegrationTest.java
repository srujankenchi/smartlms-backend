package com.smartlms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlms.dto.AssignmentRequest;
import com.smartlms.entity.Course;
import com.smartlms.entity.Lesson;
import com.smartlms.entity.Role;
import com.smartlms.entity.User;
import com.smartlms.repository.CourseRepository;
import com.smartlms.repository.LessonRepository;
import com.smartlms.repository.UserRepository;
import com.smartlms.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CourseAssignmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User teacher;
    private User student;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        teacher = userRepository.findByEmail("test.teacher@smartlms.internal")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("Teacher Test")
                        .email("test.teacher@smartlms.internal")
                        .password(passwordEncoder.encode("Password123!"))
                        .role(Role.ROLE_TEACHER)
                        .enabled(true)
                        .build()));

        student = userRepository.findByEmail("test.student@smartlms.internal")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("Student Test")
                        .email("test.student@smartlms.internal")
                        .password(passwordEncoder.encode("Password123!"))
                        .role(Role.ROLE_STUDENT)
                        .enabled(true)
                        .build()));

        org.springframework.security.core.userdetails.User teacherDetails =
                new org.springframework.security.core.userdetails.User(
                        teacher.getEmail(), teacher.getPassword(),
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TEACHER"))
                );
        teacherToken = jwtService.generateToken(teacherDetails);

        org.springframework.security.core.userdetails.User studentDetails =
                new org.springframework.security.core.userdetails.User(
                        student.getEmail(), student.getPassword(),
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STUDENT"))
                );
        studentToken = jwtService.generateToken(studentDetails);
    }

    @Test
    @DisplayName("1. Courses endpoint returns published courses with embed YouTube URLs")
    void testCoursesAndLessons() throws Exception {
        Course testCourse = courseRepository.findByTitle("Test Java Course")
                .orElseGet(() -> courseRepository.save(Course.builder()
                        .code("TEST-JAVA")
                        .title("Test Java Course")
                        .description("Test description")
                        .category("Programming")
                        .published(true)
                        .teacher(teacher)
                        .build()));

        lessonRepository.save(Lesson.builder()
                .title("Java Intro")
                .description("Intro")
                .content("Content")
                .videoUrl("https://youtu.be/eIrMbAQSU34")
                .orderIndex(1)
                .course(testCourse)
                .build());

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/courses/" + testCourse.getId() + "/lessons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].videoUrl").value("https://www.youtube.com/embed/eIrMbAQSU34"));
    }

    @Test
    @DisplayName("2. Teacher creates assignment and student submits document")
    void testAssignmentCreationAndSubmission() throws Exception {
        Course course = courseRepository.findAll().stream().findFirst().orElseGet(() ->
                courseRepository.save(Course.builder()
                        .code("ASSIGN-TEST")
                        .title("Assignment Course")
                        .published(true)
                        .teacher(teacher)
                        .build())
        );

        AssignmentRequest createReq = AssignmentRequest.builder()
                .title("Midterm Lab Report")
                .description("Submit your code report in PDF format")
                .dueDate(LocalDateTime.now().plusDays(7))
                .courseId(course.getId())
                .build();

        String createRes = mockMvc.perform(post("/api/assignments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Midterm Lab Report"))
                .andExpect(jsonPath("$.data.maxMarks").value(100))
                .andReturn().getResponse().getContentAsString();

        Long assignmentId = objectMapper.readTree(createRes).path("data").path("id").asLong();
        assertThat(assignmentId).isNotNull();

        // Student submits document (PDF)
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "solution.pdf",
                "application/pdf",
                "%PDF-1.4 sample content".getBytes()
        );

        String submitRes = mockMvc.perform(multipart("/api/assignments/" + assignmentId + "/submit")
                        .file(pdfFile)
                        .param("remarks", "All questions completed")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").isNotEmpty())
                .andExpect(jsonPath("$.data.remarks").value("All questions completed"))
                .andReturn().getResponse().getContentAsString();

        Long submissionId = objectMapper.readTree(submitRes).path("data").path("id").asLong();

        // Teacher grades submission
        com.smartlms.dto.GradeRequest gradeReq = com.smartlms.dto.GradeRequest.builder()
                .marks(94.0)
                .feedback("Excellent work! Well formatted report.")
                .build();

        mockMvc.perform(post("/api/assignments/submissions/" + submissionId + "/grade")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gradeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.marksObtained").value(94.0))
                .andExpect(jsonPath("$.data.status").value("GRADED"))
                .andExpect(jsonPath("$.data.feedback").value("Excellent work! Well formatted report."));

        // Teacher views submissions
        mockMvc.perform(get("/api/assignments/" + assignmentId + "/submissions")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].studentEmail").value("test.student@smartlms.internal"))
                .andExpect(jsonPath("$.data[0].marksObtained").value(94.0))
                .andExpect(jsonPath("$.data[0].status").value("GRADED"));
    }

    @Test
    @DisplayName("3. Public registration unconditionally assigns ROLE_STUDENT regardless of requested role")
    void testPublicRegistrationAlwaysAssignsStudent() throws Exception {
        String payload = """
                {
                    "name": "Hacker User",
                    "email": "hacker@test.com",
                    "password": "Password123!",
                    "role": "ROLE_ADMIN"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.role").value("ROLE_STUDENT"));

        User user = userRepository.findByEmail("hacker@test.com").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.ROLE_STUDENT);
    }

    @Test
    @DisplayName("4. Students are forbidden from creating assignments or grading submissions")
    void testStudentForbiddenFromTeacherEndpoints() throws Exception {
        Long testCourseId = courseRepository.findAll().stream().findFirst().map(Course::getId).orElse(1L);
        AssignmentRequest createReq = AssignmentRequest.builder()
                .title("Unauthorized Assignment")
                .description("Should fail")
                .dueDate(LocalDateTime.now().plusDays(3))
                .courseId(testCourseId)
                .build();

        mockMvc.perform(post("/api/assignments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        com.smartlms.dto.GradeRequest gradeReq = com.smartlms.dto.GradeRequest.builder()
                .marks(100.0)
                .feedback("Self grade attempt")
                .build();

        mockMvc.perform(post("/api/assignments/submissions/1/grade")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gradeReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Grade marks validation rejects negative marks or marks exceeding 100")
    void testGradeMarksValidation() throws Exception {
        com.smartlms.dto.GradeRequest invalidMarksReq = com.smartlms.dto.GradeRequest.builder()
                .marks(150.0)
                .feedback("Invalid marks test")
                .build();

        mockMvc.perform(post("/api/assignments/submissions/1/grade")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidMarksReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Login with invalid credentials returns error and message")
    void testInvalidLoginReturnsError() throws Exception {
        com.smartlms.dto.LoginRequest wrongPasswordReq = com.smartlms.dto.LoginRequest.builder()
                .email("test.teacher@smartlms.internal")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPasswordReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        com.smartlms.dto.LoginRequest nonExistentReq = com.smartlms.dto.LoginRequest.builder()
                .email("ghost.user@smartlms.internal")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonExistentReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("7. Successful login returns JWT token and verified user role")
    void testSuccessfulLoginReturnsTokenAndRole() throws Exception {
        com.smartlms.dto.LoginRequest studentLogin = com.smartlms.dto.LoginRequest.builder()
                .email("test.student@smartlms.internal")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(studentLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("ROLE_STUDENT"));

        com.smartlms.dto.LoginRequest teacherLogin = com.smartlms.dto.LoginRequest.builder()
                .email("test.teacher@smartlms.internal")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("ROLE_TEACHER"));
    }

    @Test
    @DisplayName("8. Create assignment automatically sets maxMarks to 100 and persists correctly")
    void testCreateAssignmentSetsMaxMarksTo100AndRetrieves() throws Exception {
        Course course = courseRepository.findAll().stream().findFirst().orElseThrow();

        AssignmentRequest request = AssignmentRequest.builder()
                .title("Database Schema Design & ERD")
                .description("Design normalized schemas for the library system")
                .dueDate(LocalDateTime.now().plusDays(5))
                .courseId(course.getId())
                .build();

        String res = mockMvc.perform(post("/api/assignments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Database Schema Design & ERD"))
                .andExpect(jsonPath("$.data.maxMarks").value(100))
                .andReturn().getResponse().getContentAsString();

        Long assignmentId = objectMapper.readTree(res).path("data").path("id").asLong();
        assertThat(assignmentId).isPositive();

        // Retrieve by ID and verify persistence and maxMarks = 100
        mockMvc.perform(get("/api/assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assignmentId))
                .andExpect(jsonPath("$.data.maxMarks").value(100));
    }

    @Test
    @DisplayName("9. Assignment creation fails with 400 when title is missing or blank")
    void testCreateAssignmentMissingTitleFails() throws Exception {
        Course course = courseRepository.findAll().stream().findFirst().orElseThrow();

        AssignmentRequest invalidReq = AssignmentRequest.builder()
                .title("   ")
                .description("No title")
                .dueDate(LocalDateTime.now().plusDays(3))
                .courseId(course.getId())
                .build();

        mockMvc.perform(post("/api/assignments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("10. Assignment creation fails with 400 when deadline is missing")
    void testCreateAssignmentMissingDeadlineFails() throws Exception {
        Course course = courseRepository.findAll().stream().findFirst().orElseThrow();

        AssignmentRequest invalidReq = AssignmentRequest.builder()
                .title("Missing Deadline Assignment")
                .description("No deadline specified")
                .dueDate(null)
                .courseId(course.getId())
                .build();

        mockMvc.perform(post("/api/assignments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("11. Assignment creation fails with 404 when course does not exist")
    void testCreateAssignmentInvalidCourseFails() throws Exception {
        AssignmentRequest invalidReq = AssignmentRequest.builder()
                .title("Non-existent Course Assignment")
                .description("Invalid course ID")
                .dueDate(LocalDateTime.now().plusDays(3))
                .courseId(999999L)
                .build();

        mockMvc.perform(post("/api/assignments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("12. Assignment creation without authentication fails with 401")
    void testCreateAssignmentUnauthenticatedFails() throws Exception {
        Course course = courseRepository.findAll().stream().findFirst().orElseThrow();

        AssignmentRequest request = AssignmentRequest.builder()
                .title("Unauthenticated Post")
                .dueDate(LocalDateTime.now().plusDays(3))
                .courseId(course.getId())
                .build();

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
