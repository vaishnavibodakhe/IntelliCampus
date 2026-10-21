package com.intellicampus.user.controller;

import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import com.intellicampus.security.config.SecurityConfig;
import com.intellicampus.auth.service.JwtTokenProvider;
import com.intellicampus.user.dto.CaptureTokenResponseDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.TokenDenylistRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.FaceEnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentController.class)
@Import(SecurityConfig.class)
class StudentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FaceEnrollmentService faceEnrollmentService;

    @MockBean
    private StudentProfileRepository studentProfileRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private TokenDenylistRepository tokenDenylistRepository;

    @BeforeEach
    void setUp() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.ServletRequest request = invocation.getArgument(0);
            jakarta.servlet.ServletResponse response = invocation.getArgument(1);
            jakarta.servlet.FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    // =========================================================================
    // 1. Unauthenticated tests
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated GET /api/students/1 returns 401 Unauthorized")
    void testUnauthenticatedGetStudentReturns401() throws Exception {
        mockMvc.perform(get("/api/students/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated GET /api/students/division/1 returns 401 Unauthorized")
    void testUnauthenticatedGetDivisionReturns401() throws Exception {
        mockMvc.perform(get("/api/students/division/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. permitAll without JWT: capture-token and face-enroll
    // =========================================================================

    @Test
    @DisplayName("POST /api/students/1/capture-token works without a JWT (200 OK)")
    void testCaptureTokenWorksWithoutJwt() throws Exception {
        when(faceEnrollmentService.issueCaptureToken(eq(1L), any()))
                .thenReturn(new CaptureTokenResponseDto("cap-token-xyz-123", 1L, 300L, java.time.Instant.now().plusSeconds(300)));

        mockMvc.perform(post("/api/students/1/capture-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("cap-token-xyz-123"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.studentId").value(1L));
    }

    @Test
    @DisplayName("POST /api/students/1/face-enroll works without a JWT (200 OK)")
    void testFaceEnrollWorksWithoutJwt() throws Exception {
        StudentProfile profile = new StudentProfile();
        profile.setId(1L);
        profile.setRollNo("CS-101");
        profile.setFaceEnrolled(true);
        profile.setConsentVersion("1.0");

        when(faceEnrollmentService.enrollStudentFace(eq(1L), any(), any()))
                .thenReturn(profile);

        String payload = """
                {
                    "token": "cap-token-xyz-123",
                    "frames": [
                        "data:image/jpeg;base64,frameFrontData",
                        "data:image/jpeg;base64,frameLeftData",
                        "data:image/jpeg;base64,frameRightData"
                    ],
                    "liveness": true,
                    "consentVersion": "1.0"
                }
                """;

        mockMvc.perform(post("/api/students/1/face-enroll")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.studentId").value(1L))
                .andExpect(jsonPath("$.faceEnrolled").value(true));
    }

    // =========================================================================
    // 3. Student authorization: Student A cannot read Student B (403 Forbidden)
    // =========================================================================

    @Test
    @WithMockUser(username = "studentA@intellicampus.edu", roles = {"STUDENT"})
    @DisplayName("Student A cannot read Student B record (403 Forbidden)")
    void testStudentCannotReadOtherStudentRecord() throws Exception {
        User userB = new User("Student B", "studentB@intellicampus.edu", "hashB", Role.STUDENT, "STU-B");
        userB.setId(200L);
        StudentProfile profileB = new StudentProfile();
        profileB.setId(2L);
        profileB.setUser(userB);
        profileB.setRollNo("CS-102");

        when(faceEnrollmentService.findStudent(2L)).thenReturn(profileB);

        mockMvc.perform(get("/api/students/2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "studentA@intellicampus.edu", roles = {"STUDENT"})
    @DisplayName("Student A can read own student record (200 OK)")
    void testStudentCanReadOwnRecord() throws Exception {
        User userA = new User("Student A", "studentA@intellicampus.edu", "hashA", Role.STUDENT, "STU-A");
        userA.setId(100L);
        StudentProfile profileA = new StudentProfile();
        profileA.setId(1L);
        profileA.setUser(userA);
        profileA.setRollNo("CS-101");

        when(faceEnrollmentService.findStudent(1L)).thenReturn(profileA);

        mockMvc.perform(get("/api/students/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("studentA@intellicampus.edu"))
                .andExpect(jsonPath("$.rollNo").value("CS-101"));
    }

    @Test
    @WithMockUser(username = "studentA@intellicampus.edu", roles = {"STUDENT"})
    @DisplayName("Student cannot read division list (403 Forbidden)")
    void testStudentCannotReadDivisionList() throws Exception {
        mockMvc.perform(get("/api/students/division/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. Staff authorization: ADMIN / HOD / FACULTY / MENTOR
    // =========================================================================

    @Test
    @WithMockUser(username = "faculty@intellicampus.edu", roles = {"FACULTY"})
    @DisplayName("Faculty can read any student record (200 OK)")
    void testFacultyCanReadStudentRecord() throws Exception {
        User userB = new User("Student B", "studentB@intellicampus.edu", "hashB", Role.STUDENT, "STU-B");
        userB.setId(200L);
        StudentProfile profileB = new StudentProfile();
        profileB.setId(2L);
        profileB.setUser(userB);
        profileB.setRollNo("CS-102");

        when(faceEnrollmentService.findStudent(2L)).thenReturn(profileB);

        mockMvc.perform(get("/api/students/2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.email").value("studentB@intellicampus.edu"));
    }

    @Test
    @WithMockUser(username = "admin@intellicampus.edu", roles = {"ADMIN"})
    @DisplayName("Admin can read any student record (200 OK)")
    void testAdminCanReadStudentRecord() throws Exception {
        User userB = new User("Student B", "studentB@intellicampus.edu", "hashB", Role.STUDENT, "STU-B");
        userB.setId(200L);
        StudentProfile profileB = new StudentProfile();
        profileB.setId(2L);
        profileB.setUser(userB);
        profileB.setRollNo("CS-102");

        when(faceEnrollmentService.findStudent(2L)).thenReturn(profileB);

        mockMvc.perform(get("/api/students/2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "faculty@intellicampus.edu", roles = {"FACULTY"})
    @DisplayName("Faculty can read division list (200 OK)")
    void testFacultyCanReadDivisionList() throws Exception {
        User user = new User("Student In Div", "student.div@intellicampus.edu", "hash", Role.STUDENT, "STU-DIV");
        StudentProfile profile = new StudentProfile();
        profile.setId(10L);
        profile.setUser(user);
        profile.setRollNo("CS-110");

        when(studentProfileRepository.findByDivisionId(1L)).thenReturn(List.of(profile));

        mockMvc.perform(get("/api/students/division/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].rollNo").value("CS-110"));
    }

    @Test
    @WithMockUser(username = "hod@intellicampus.edu", roles = {"HOD"})
    @DisplayName("HOD can read division list (200 OK)")
    void testHodCanReadDivisionList() throws Exception {
        when(studentProfileRepository.findByDivisionId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/students/division/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
