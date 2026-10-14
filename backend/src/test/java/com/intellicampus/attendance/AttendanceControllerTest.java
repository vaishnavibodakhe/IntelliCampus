package com.intellicampus.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellicampus.attendance.controller.AttendanceController;
import com.intellicampus.attendance.dto.*;
import com.intellicampus.attendance.model.AttendanceStatus;
import com.intellicampus.attendance.model.SessionStatus;
import com.intellicampus.attendance.service.AttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc test suite verifying HTTP contracts for /api/attendance/sessions REST API.
 * Compliant with IC-SRS-1.0 Section 8.6 & Test Cases TC-02/TC-03.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private AttendanceService attendanceService;

    @InjectMocks
    private AttendanceController attendanceController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(attendanceController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/attendance/sessions - Starts attendance session and returns 201 CREATED")
    void testStartSessionEndpoint() throws Exception {
        StartSessionRequestDto req = new StartSessionRequestDto();
        req.setFacultyId(1L);
        req.setSubjectId(5L);
        req.setDivisionId(3L);
        req.setRoomId(7L);
        req.setChallengeCode("K7Q2");
        req.setPhotoWindowMinutes(10);

        SessionResponseDto responseDto = new SessionResponseDto();
        responseDto.setId(100L);
        responseDto.setFacultyName("Dr. Ramesh Joshi");
        responseDto.setSubjectCode("CS301");
        responseDto.setSubjectName("Distributed Systems");
        responseDto.setRoomName("Lab 301");
        responseDto.setDivisionName("TE-CS-A");
        responseDto.setChallengeCode("K7Q2");
        responseDto.setStatus(SessionStatus.OPEN);
        responseDto.setStartedAt(LocalDateTime.now());

        when(attendanceService.startSession(any(StartSessionRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.challengeCode").value("K7Q2"))
                .andExpect(jsonPath("$.facultyName").value("Dr. Ramesh Joshi"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(attendanceService).startSession(any(StartSessionRequestDto.class));
    }

    @Test
    @DisplayName("GET /api/attendance/sessions/{id} - Returns session details with 200 OK")
    void testGetSessionEndpoint() throws Exception {
        SessionResponseDto responseDto = new SessionResponseDto();
        responseDto.setId(100L);
        responseDto.setChallengeCode("K7Q2");
        responseDto.setStatus(SessionStatus.OPEN);

        when(attendanceService.getSessionDetails(100L)).thenReturn(responseDto);

        mockMvc.perform(get("/api/attendance/sessions/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.challengeCode").value("K7Q2"));

        verify(attendanceService).getSessionDetails(100L);
    }

    @Test
    @DisplayName("GET /api/attendance/sessions/{id}/token - Returns dynamic 15-second rotating QR token with 200 OK")
    void testGetDynamicQrTokenEndpoint() throws Exception {
        DynamicQrTokenDto tokenDto = new DynamicQrTokenDto(123456L, "IC-A1B2C3D4E5F6", 12);
        when(attendanceService.generateDynamicQrToken(100L)).thenReturn(tokenDto);

        mockMvc.perform(get("/api/attendance/sessions/100/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slot").value(123456))
                .andExpect(jsonPath("$.token").value("IC-A1B2C3D4E5F6"))
                .andExpect(jsonPath("$.secondsLeft").value(12));

        verify(attendanceService).generateDynamicQrToken(100L);
    }

    @Test
    @DisplayName("POST /api/attendance/sessions/{id}/confirm - Confirms student check-in with 200 OK")
    void testConfirmAttendanceEndpoint() throws Exception {
        StudentConfirmAttendanceDto req = new StudentConfirmAttendanceDto();
        req.setStudentId(10L);
        req.setChallengeCode("K7Q2");
        req.setLatitude(18.52044);
        req.setLongitude(73.85675);
        req.setAccuracyM(15.0);

        AttendanceRecordResponseDto recordDto = new AttendanceRecordResponseDto();
        recordDto.setId(501L);
        recordDto.setStudentId(10L);
        recordDto.setStudentName("Aarav Mehta");
        recordDto.setRollNo("2024-CS-042");
        recordDto.setConfirmed(true);
        recordDto.setFinalStatus(AttendanceStatus.PRESENT);
        recordDto.setStatusReason("Verified via GPS Geofence (15m) and Dynamic QR Token.");

        when(attendanceService.confirmStudentAttendance(eq(100L), any(StudentConfirmAttendanceDto.class)))
                .thenReturn(recordDto);

        mockMvc.perform(post("/api/attendance/sessions/100/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(10))
                .andExpect(jsonPath("$.studentName").value("Aarav Mehta"))
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.finalStatus").value("PRESENT"));

        verify(attendanceService).confirmStudentAttendance(eq(100L), any(StudentConfirmAttendanceDto.class));
    }

    @Test
    @DisplayName("PUT /api/attendance/sessions/{id}/status - Transitions status (e.g. FINALISED) with 200 OK")
    void testTransitionStatusEndpoint() throws Exception {
        SessionResponseDto responseDto = new SessionResponseDto();
        responseDto.setId(100L);
        responseDto.setStatus(SessionStatus.FINALISED);

        when(attendanceService.transitionSessionStatus(100L, SessionStatus.FINALISED)).thenReturn(responseDto);

        mockMvc.perform(put("/api/attendance/sessions/100/status")
                        .param("status", "FINALISED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("FINALISED"));

        verify(attendanceService).transitionSessionStatus(100L, SessionStatus.FINALISED);
    }

    @Test
    @DisplayName("PUT /api/attendance/sessions/{id}/records/{studentId}/override - Overrides attendance status with 200 OK")
    void testOverrideRecordEndpoint() throws Exception {
        ManualOverrideDto override = new ManualOverrideDto();
        override.setStatus(AttendanceStatus.PRESENT);
        override.setReason("Faculty manual verification");

        AttendanceRecordResponseDto recordDto = new AttendanceRecordResponseDto();
        recordDto.setId(501L);
        recordDto.setStudentId(10L);
        recordDto.setFinalStatus(AttendanceStatus.PRESENT);
        recordDto.setStatusReason("Faculty manual verification");

        when(attendanceService.overrideRecordStatus(eq(100L), eq(10L), any(ManualOverrideDto.class)))
                .thenReturn(recordDto);

        mockMvc.perform(put("/api/attendance/sessions/100/records/10/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(override)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(10))
                .andExpect(jsonPath("$.finalStatus").value("PRESENT"))
                .andExpect(jsonPath("$.statusReason").value("Faculty manual verification"));

        verify(attendanceService).overrideRecordStatus(eq(100L), eq(10L), any(ManualOverrideDto.class));
    }

    @Test
    @DisplayName("GET /api/attendance/sessions/active - Returns active session or 204 NO CONTENT")
    void testGetActiveSessionEndpoint() throws Exception {
        SessionResponseDto responseDto = new SessionResponseDto();
        responseDto.setId(100L);
        responseDto.setStatus(SessionStatus.OPEN);

        when(attendanceService.getActiveSessionForDivision(3L)).thenReturn(Optional.of(responseDto));
        when(attendanceService.getActiveSessionForDivision(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/attendance/sessions/active").param("divisionId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));

        mockMvc.perform(get("/api/attendance/sessions/active").param("divisionId", "99"))
                .andExpect(status().isNoContent());
    }
}
