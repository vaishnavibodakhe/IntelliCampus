package com.intellicampus.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellicampus.attendance.controller.AttendanceController;
import com.intellicampus.attendance.dto.ManualOverrideDto;
import com.intellicampus.attendance.dto.PhotoRecognitionResultDto;
import com.intellicampus.attendance.dto.SessionResponseDto;
import com.intellicampus.attendance.dto.StartSessionRequestDto;
import com.intellicampus.attendance.model.SessionStatus;
import com.intellicampus.attendance.service.AttendanceService;
import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import com.intellicampus.security.config.SecurityConfig;
import com.intellicampus.auth.service.JwtTokenProvider;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceController.class)
@Import(SecurityConfig.class)
class AttendanceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttendanceService attendanceService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.ServletRequest request = invocation.getArgument(0);
            jakarta.servlet.ServletResponse response = invocation.getArgument(1);
            jakarta.servlet.FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("Unauthenticated request to POST /api/attendance/sessions returns 401 UNAUTHORIZED")
    void testUnauthenticatedStartSession() throws Exception {
        StartSessionRequestDto req = new StartSessionRequestDto();
        req.setFacultyId(1L);
        req.setSubjectId(5L);
        req.setDivisionId(3L);
        req.setRoomId(7L);

        mockMvc.perform(post("/api/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to upload classroom photo returns 401 UNAUTHORIZED")
    void testUnauthenticatedUploadPhoto() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/attendance/sessions/100/photo")
                        .file(photo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("Student role cannot upload classroom photo (403 FORBIDDEN)")
    void testStudentCannotUploadPhoto() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/attendance/sessions/100/photo")
                        .file(photo))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("Student role cannot override attendance record (403 FORBIDDEN)")
    void testStudentCannotOverrideRecord() throws Exception {
        ManualOverrideDto override = new ManualOverrideDto();
        override.setStatus(com.intellicampus.attendance.model.AttendanceStatus.PRESENT);

        mockMvc.perform(put("/api/attendance/sessions/100/records/10/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(override)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FACULTY")
    @DisplayName("Faculty role can create attendance session (201 CREATED)")
    void testFacultyCanStartSession() throws Exception {
        StartSessionRequestDto req = new StartSessionRequestDto();
        req.setFacultyId(1L);
        req.setSubjectId(5L);
        req.setDivisionId(3L);
        req.setRoomId(7L);

        SessionResponseDto responseDto = new SessionResponseDto();
        responseDto.setId(101L);
        when(attendanceService.startSession(any(StartSessionRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "FACULTY")
    @DisplayName("Faculty role can upload classroom photo (200 OK)")
    void testFacultyCanUploadPhoto() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        PhotoRecognitionResultDto resultDto = new PhotoRecognitionResultDto();
        resultDto.setSessionId(100L);
        when(attendanceService.processClassroomPhoto(eq(100L), any(), any(), any(), any())).thenReturn(resultDto);

        mockMvc.perform(multipart("/api/attendance/sessions/100/photo")
                        .file(photo)
                        .param("latitude", "18.52043")
                        .param("longitude", "73.85674")
                        .param("accuracyM", "10.0"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("Student role can view own attendance record history (200 OK)")
    void testStudentCanViewOwnAttendance() throws Exception {
        when(attendanceService.getRecordsForStudent(10L)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/attendance/sessions/records/student/10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PRINCIPAL")
    @DisplayName("Principal role can update session status to FINALISED (200 OK)")
    void testAdminCanFinaliseSession() throws Exception {
        SessionResponseDto responseDto = new SessionResponseDto();
        responseDto.setId(100L);
        responseDto.setStatus(SessionStatus.FINALISED);
        when(attendanceService.transitionSessionStatus(eq(100L), eq(SessionStatus.FINALISED))).thenReturn(responseDto);

        mockMvc.perform(put("/api/attendance/sessions/100/status")
                        .param("status", "FINALISED"))
                .andExpect(status().isOk());
    }
}
