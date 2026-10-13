package com.smartlms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlms.dto.LoginRequest;
import com.smartlms.dto.RegisterRequest;
import com.smartlms.entity.Role;
import com.smartlms.entity.User;
import com.smartlms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.smartlms.security.JwtService jwtService;

    @Autowired
    private com.smartlms.repository.SubmissionRepository submissionRepository;

    @Autowired
    private com.smartlms.repository.AssignmentRepository assignmentRepository;

    @BeforeEach
    void setUp() {
        cleanUpAuthTestUsers();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cleanUpAuthTestUsers();
    }

    private void cleanUpAuthTestUsers() {
        java.util.List<String> authTestEmails = java.util.List.of(
                "student.test@smartlms.internal",
                "login.student@smartlms.internal",
                "me.student@smartlms.internal",
                "dup.student@smartlms.internal",
                "hacker@smartlms.internal",
                "bcrypt.student@smartlms.internal",
                "hex.test@smartlms.internal"
        );

        for (String email : authTestEmails) {
            userRepository.findByEmail(email).ifPresent(user -> {
                // 1. Delete dependent submissions submitted by this test student
                submissionRepository.deleteAll(submissionRepository.findByStudentId(user.getId()));

                // 2. Delete dependent assignments (and their submissions) created by this test teacher
                java.util.List<com.smartlms.entity.Assignment> assignments = assignmentRepository.findByTeacherId(user.getId());
                for (com.smartlms.entity.Assignment assignment : assignments) {
                    submissionRepository.deleteAll(submissionRepository.findByAssignmentId(assignment.getId()));
                    assignmentRepository.delete(assignment);
                }

                // 3. Delete the test user safely
                userRepository.delete(user);
            });
        }
    }

    @Test
    @DisplayName("1. Register a new student account successfully")
    void testRegisterStudent() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("Test Student")
                .email("student.test@smartlms.internal")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("student.test@smartlms.internal"))
                .andExpect(jsonPath("$.data.user.role").value("ROLE_STUDENT"));
    }

    @Test
    @DisplayName("2. Login with valid credentials returns JWT")
    void testLoginValid() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .name("Login Student")
                .email("login.student@smartlms.internal")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = LoginRequest.builder()
                .email("login.student@smartlms.internal")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("login.student@smartlms.internal"));
    }

    @Test
    @DisplayName("3. Verify JWT with GET /api/auth/me")
    void testGetCurrentUserWithJwt() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .name("Me Student")
                .email("me.student@smartlms.internal")
                .password("Password123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).path("data").path("token").asText();

        // Calling /api/auth/me with Bearer token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("me.student@smartlms.internal"))
                .andExpect(jsonPath("$.data.role").value("ROLE_STUDENT"));

        // Calling /api/auth/me without token returns 401
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. Verify duplicate registration and incorrect passwords are rejected")
    void testDuplicateRegistrationAndWrongPassword() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .name("Dup Student")
                .email("dup.student@smartlms.internal")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Duplicate registration must fail with 400
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Incorrect password on login must fail with 400
        LoginRequest wrongLoginReq = LoginRequest.builder()
                .email("dup.student@smartlms.internal")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongLoginReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("5. Public registration cannot create Admin or Teacher accounts")
    void testRegistrationCannotCreateAdminOrTeacher() throws Exception {
        String payload = "{\"name\":\"Hacker\",\"email\":\"hacker@smartlms.internal\",\"password\":\"Password123!\",\"role\":\"ROLE_ADMIN\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value("ROLE_STUDENT"));

        User user = userRepository.findByEmail("hacker@smartlms.internal").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.ROLE_STUDENT);
    }

    @Test
    @DisplayName("6. Passwords are stored as BCrypt hashes")
    void testPasswordStoredAsBcrypt() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .name("Bcrypt Student")
                .email("bcrypt.student@smartlms.internal")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("bcrypt.student@smartlms.internal").orElseThrow();
        assertThat(user.getPassword()).startsWith("$2a$");
        assertThat(user.getPassword()).isNotEqualTo("Password123!");
        assertThat(passwordEncoder.matches("Password123!", user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("7. Hexadecimal JWT secret validation: valid 256-bit hex works, short or invalid hex rejected")
    void testHexSecretValidation() {
        String originalSecret = (String) org.springframework.test.util.ReflectionTestUtils.getField(jwtService, "secretKey");
        try {
            // Valid 64-character (32-byte / 256-bit) hex secret
            String validHexSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
            org.springframework.test.util.ReflectionTestUtils.setField(jwtService, "secretKey", validHexSecret);

            org.springframework.security.core.userdetails.User testUser =
                    new org.springframework.security.core.userdetails.User(
                            "hex.test@smartlms.internal",
                            "password",
                            java.util.Collections.emptyList()
                    );

            String token = jwtService.generateToken(testUser);
            assertThat(token).isNotEmpty();
            assertThat(jwtService.extractUsername(token)).isEqualTo("hex.test@smartlms.internal");
            assertThat(jwtService.isTokenValid(token, testUser)).isTrue();

            // Short hex secret (< 32 bytes / < 64 chars, e.g. 48 hex chars = 24 bytes / 192 bits)
            String shortHexSecret = "404E635266556A586E3272357538782F413F4428472B4B62";
            org.springframework.test.util.ReflectionTestUtils.setField(jwtService, "secretKey", shortHexSecret);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                jwtService.generateToken(testUser);
            });

            // Invalid non-hex characters must throw IllegalStateException
            String invalidHexSecret = "404E635266556A586E327235753878_Z!INVALID_NON_HEX_STRING_HERE!!";
            org.springframework.test.util.ReflectionTestUtils.setField(jwtService, "secretKey", invalidHexSecret);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                jwtService.generateToken(testUser);
            });
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(jwtService, "secretKey", originalSecret);
        }
    }
}
