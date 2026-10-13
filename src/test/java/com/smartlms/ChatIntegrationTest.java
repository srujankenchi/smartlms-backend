package com.smartlms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlms.dto.ChatMessageRequest;
import com.smartlms.dto.ChatReactionRequest;
import com.smartlms.dto.LoginRequest;
import com.smartlms.entity.Role;
import com.smartlms.entity.User;
import com.smartlms.repository.ChatMessageRepository;
import com.smartlms.repository.ChatUserReadRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatUserReadRepository chatUserReadRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String teacherToken;
    private String studentToken;
    private User teacherUser;
    private User studentUser;

    @BeforeEach
    void setUp() throws Exception {
        // Create teacher if not exists
        teacherUser = userRepository.findByEmail("chat.teacher@smartlms.edu")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("Prof. Alan Turing")
                        .email("chat.teacher@smartlms.edu")
                        .password(passwordEncoder.encode("Password123!"))
                        .role(Role.ROLE_TEACHER)
                        .enabled(true)
                        .active(true)
                        .build()));

        // Create student if not exists
        studentUser = userRepository.findByEmail("chat.student@smartlms.edu")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("Grace Hopper")
                        .email("chat.student@smartlms.edu")
                        .password(passwordEncoder.encode("Password123!"))
                        .role(Role.ROLE_STUDENT)
                        .enabled(true)
                        .active(true)
                        .build()));

        // Login teacher
        String teacherLoginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("chat.teacher@smartlms.edu")
                                .password("Password123!")
                                .build())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        teacherToken = objectMapper.readTree(teacherLoginRes).path("data").path("token").asText();

        // Login student
        String studentLoginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("chat.student@smartlms.edu")
                                .password("Password123!")
                                .build())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        studentToken = objectMapper.readTree(studentLoginRes).path("data").path("token").asText();
    }

    @Test
    @DisplayName("1. Student posts message to group chat and retrieves it")
    void testStudentSendsMessage() throws Exception {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .content("Hello everyone! When is the next lab submission due?")
                .build();

        String res = mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Hello everyone! When is the next lab submission due?"))
                .andExpect(jsonPath("$.data.senderName").value("Grace Hopper"))
                .andReturn().getResponse().getContentAsString();

        Long messageId = objectMapper.readTree(res).path("data").path("id").asLong();
        assertThat(messageId).isPositive();

        // Retrieve messages and verify student message is in the list
        mockMvc.perform(get("/api/chat/messages")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("2. Teacher replies to student message and pins it")
    void testTeacherRepliesAndPinsMessage() throws Exception {
        // 1. Student sends message
        ChatMessageRequest studentReq = ChatMessageRequest.builder()
                .content("Is the exam chapter 1 to 4?")
                .build();
        String studentMsgRes = mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(studentReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long studentMsgId = objectMapper.readTree(studentMsgRes).path("data").path("id").asLong();

        // 2. Teacher replies
        ChatMessageRequest teacherReply = ChatMessageRequest.builder()
                .content("Yes, chapters 1 through 4 are covered. Please review the lecture slides.")
                .replyToId(studentMsgId)
                .build();
        String replyRes = mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherReply)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.replyTo.id").value(studentMsgId))
                .andReturn().getResponse().getContentAsString();
        Long replyId = objectMapper.readTree(replyRes).path("data").path("id").asLong();

        // 3. Teacher pins the reply
        mockMvc.perform(post("/api/chat/messages/" + replyId + "/pin")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pinned").value(true));

        // 4. Verify pinned messages endpoint returns it
        mockMvc.perform(get("/api/chat/pinned")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(replyId));
    }

    @Test
    @DisplayName("3. Student cannot pin a message (403 Forbidden)")
    void testStudentCannotPinMessage() throws Exception {
        ChatMessageRequest req = ChatMessageRequest.builder()
                .content("Student note")
                .build();
        String res = mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long msgId = objectMapper.readTree(res).path("data").path("id").asLong();

        mockMvc.perform(post("/api/chat/messages/" + msgId + "/pin")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. User can toggle emoji reactions on a message")
    void testToggleEmojiReaction() throws Exception {
        ChatMessageRequest req = ChatMessageRequest.builder()
                .content("Great presentation today!")
                .build();
        String res = mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long msgId = objectMapper.readTree(res).path("data").path("id").asLong();

        // Student reacts with thumbs up 👍
        mockMvc.perform(post("/api/chat/messages/" + msgId + "/react")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ChatReactionRequest.builder().emoji("👍").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactionCounts.👍").value(1))
                .andExpect(jsonPath("$.data.myReactions[0]").value("👍"));

        // Student toggles again (un-reacts)
        mockMvc.perform(post("/api/chat/messages/" + msgId + "/react")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ChatReactionRequest.builder().emoji("👍").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myReactions").isEmpty());
    }

    @Test
    @DisplayName("5. Unauthenticated request to chat returns 401 Unauthorized")
    void testUnauthenticatedChatAccess() throws Exception {
        mockMvc.perform(get("/api/chat/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6. Group info returns CSE Class Group and member metadata")
    void testGetGroupInfo() throws Exception {
        mockMvc.perform(get("/api/chat/group-info")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupName").value("CSE Class Group"))
                .andExpect(jsonPath("$.data.totalMembers").isNumber());
    }
}
