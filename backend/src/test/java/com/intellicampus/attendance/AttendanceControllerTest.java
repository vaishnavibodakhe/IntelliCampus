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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
    @DisplayName("POST /api/attendance/sessions/{id}/photo - Uploads classroom photo and returns recognition result")
    void testUploadClassroomPhotoEndpoint() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "classroom.jpg", "image/jpeg", "dummy image content".getBytes()
        );

        PhotoRecognitionResultDto resultDto = new PhotoRecognitionResultDto();
        resultDto.setSessionId(100L);
        resultDto.setPhotoId(1L);
        resultDto.setPhotoIndex(1);
        resultDto.setTotalPhotos(1);
        resultDto.setFacesDetected(15);
        resultDto.setStudentsMatched(12);
        resultDto.setStudentsInReview(2);
        resultDto.setUnmatchedFaceCount(1);
        resultDto.setMessage("Processed 15 faces");

        when(attendanceService.processClassroomPhoto(eq(100L), any(), any(), any(), any()))
                .thenReturn(resultDto);

        mockMvc.perform(multipart("/api/attendance/sessions/100/photo")
                        .file(photo)
                        .param("latitude", "18.52043")
                        .param("longitude", "73.85674")
                        .param("accuracyM", "10.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(100))
                .andExpect(jsonPath("$.facesDetected").value(15))
                .andExpect(jsonPath("$.studentsMatched").value(12))
                .andExpect(jsonPath("$.studentsInReview").value(2))
                .andExpect(jsonPath("$.unmatchedFaceCount").value(1))
                .andExpect(jsonPath("$.unmatchedFaces").value(1));

        verify(attendanceService).processClassroomPhoto(eq(100L), any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/attendance/sessions/records/student/{studentId} - Returns student attendance records")
    void testGetStudentAttendanceRecordsEndpoint() throws Exception {
        AttendanceRecordResponseDto recordDto = new AttendanceRecordResponseDto();
        recordDto.setId(501L);
        recordDto.setStudentId(10L);
        recordDto.setStudentName("Aarav Mehta");
        recordDto.setRollNo("2024-CS-042");
        recordDto.setFinalStatus(AttendanceStatus.PRESENT);

        when(attendanceService.getRecordsForStudent(10L)).thenReturn(List.of(recordDto));

        mockMvc.perform(get("/api/attendance/sessions/records/student/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(10))
                .andExpect(jsonPath("$[0].studentName").value("Aarav Mehta"))
                .andExpect(jsonPath("$[0].finalStatus").value("PRESENT"));

        verify(attendanceService).getRecordsForStudent(10L);
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
        recordDto.setStatusReason("Faculty manual override: Faculty manual verification");

        when(attendanceService.overrideRecordStatus(eq(100L), eq(10L), any(ManualOverrideDto.class)))
                .thenReturn(recordDto);

        mockMvc.perform(put("/api/attendance/sessions/100/records/10/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(override)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(10))
                .andExpect(jsonPath("$.finalStatus").value("PRESENT"))
                .andExpect(jsonPath("$.statusReason").value("Faculty manual override: Faculty manual verification"));

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